package ru.medovik.hack2026.api.service.ml

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import ru.medovik.hack2026.api.dto.request.risk.Service as BillingService
import ru.medovik.hack2026.api.properties.MlProperties
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class MlPredictionService(private val properties: MlProperties, private val json: JsonMapper) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val http = HttpClient.newBuilder().connectTimeout(properties.requestTimeout)
        .version(HttpClient.Version.HTTP_1_1).followRedirects(HttpClient.Redirect.NEVER).build()
    private data class CacheKey(val revision: String, val at: Instant, val ids: List<Long>, val model: String)
    private data class Cached(val expires: Long, val batch: MlPredictionBatch)
    private val cache = ConcurrentHashMap<CacheKey, Cached>()

    /** Called through the Spring proxy: the dashboard read transaction is suspended
     * while bounded network I/O runs. Input history was captured before suspension. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun predict(revision: String, at: Instant, services: List<BillingService>, history: MlHistory): MlPredictionBatch {
        if (!properties.enabled) return MlPredictionBatch.fallback(services, "ML_DISABLED")
        if (services.isEmpty()) return MlPredictionBatch(emptyMap())
        if (services.size > 1000) return MlPredictionBatch.fallback(services, "REQUEST_SERVICE_LIMIT")
        val key = CacheKey(revision, at, services.map { it.id }, properties.modelVersion)
        val now = System.nanoTime()
        cache[key]?.takeIf { it.expires > now }?.let { return it.batch }
        val request = MlRequest(requestId = UUID.randomUUID().toString(), snapshotRevision = revision,
            predictionAt = at, serviceIds = services.map { it.id }, input = MlHistoryInput(history = history))
        val batch = try {
            val body = json.writeValueAsBytes(request)
            if (body.size > 32 * 1024 * 1024) return MlPredictionBatch.fallback(services, "REQUEST_HISTORY_LIMIT")
            val response = http.send(HttpRequest.newBuilder(URI(properties.baseUrl.trimEnd('/') + "/internal/v1/renewal-risk/predict"))
                .header("Authorization", "Bearer ${properties.serviceToken}")
                .header("Content-Type", "application/json")
                .timeout(properties.requestTimeout).POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(),
                HttpResponse.BodyHandlers.ofByteArray())
            if (response.statusCode() != 200 || response.body().size > 8 * 1024 * 1024) {
                logger.warn("ML prediction unavailable: HTTP {}, responseBytes={}", response.statusCode(), response.body().size)
                MlPredictionBatch.fallback(services, "ML_UNAVAILABLE")
            } else validate(json.readTree(response.body()), request, services)
        } catch (exception: Exception) {
            if (exception is InterruptedException) Thread.currentThread().interrupt()
            // Neither request bodies, credentials nor remote exception text enter logs.
            logger.warn("ML prediction unavailable: {}", exception.javaClass.simpleName)
            MlPredictionBatch.fallback(services, "ML_UNAVAILABLE")
        }
        // Failures are not memoized: a recovered service can correct the next request.
        if (batch.modelVersion != null && !properties.cacheTtl.isZero &&
            batch.outcomes.values.none { it.reason in setOf("ML_RESULT_INVALID", "ML_RESULT_MISSING", "ML_RESULT_DUPLICATE") }) {
            if (cache.size >= 64) cache.clear()
            cache[key] = Cached(System.nanoTime() + properties.cacheTtl.toNanos(), batch)
        }
        return batch
    }

    internal fun validate(root: JsonNode, request: MlRequest, services: List<BillingService>): MlPredictionBatch {
        fun text(node: JsonNode?, field: String): String? = node?.get(field)?.takeIf { it.isString }?.stringValue()
        val predictions = root.get("predictions")
        if (text(root, "requestId") != request.requestId || text(root, "contractVersion") != ML_CONTRACT ||
            text(root, "snapshotRevision") != request.snapshotRevision ||
            text(root, "predictionAt")?.let { runCatching { Instant.parse(it) }.getOrNull() } != request.predictionAt ||
            text(root, "modelVersion") != properties.modelVersion || text(root, "featureSchemaVersion") != ML_FEATURES ||
            text(root, "targetVersion") != ML_TARGET || root.get("syntheticOnly")?.isBoolean != true ||
            predictions?.isArray != true
        ) return MlPredictionBatch.fallback(services, "ML_RESPONSE_MISMATCH")
        val byId = predictions.toList().filter { it.get("serviceId")?.isIntegralNumber == true && it.get("serviceId").canConvertToLong() }
            .groupBy { it.get("serviceId").longValue() }
        val outcomes = services.associate { service ->
            val entries = byId[service.id]
            val item = entries?.singleOrNull()
            val outcome = if (item == null) MlOutcome(reason = if (entries == null) "ML_RESULT_MISSING" else "ML_RESULT_DUPLICATE")
            else {
                val status = text(item, "status")
                val p = item.get("lossProbability")
                val owner = item.get("accountId")
                val due = text(item, "dueAt")?.let { runCatching { Instant.parse(it) }.getOrNull() }
                val deadline = text(item, "outcomeDeadline")?.let { runCatching { Instant.parse(it) }.getOrNull() }
                if (status == "predicted") {
                    if (owner?.isIntegralNumber == true && owner.canConvertToLong() && owner.longValue() == service.ownerId &&
                        due == service.expiresAt && deadline == service.expiresAt.plus(Duration.ofDays(5)) &&
                        p?.isNumber == true && p.doubleValue().isFinite() && p.doubleValue() in 0.0..1.0 &&
                        item.get("reasonCode")?.isNull == true && item.get("warnings")?.isArray == true
                    ) MlOutcome(probability = p.doubleValue()) else MlOutcome(reason = "ML_RESULT_INVALID")
                } else if (status in setOf("not_applicable", "insufficient_data", "invalid_data") && p?.isNull == true) {
                    val reason = text(item, "reasonCode")
                    MlOutcome(reason = if (reason in REASONS) reason else "ML_RESULT_INVALID")
                } else MlOutcome(reason = "ML_RESULT_INVALID")
            }
            service.id to outcome
        }
        return MlPredictionBatch(outcomes, properties.modelVersion)
    }

    private companion object {
        val REASONS = setOf("SERVICE_NOT_FOUND", "UNSUPPORTED_PAYMENT_TERM", "OUTSIDE_PREDICTION_WINDOW", "OVERDUE_SERVICE",
            "NONFINANCIAL_RESTRICTION", "DELETED_OR_REPLACED", "SNAPSHOT_HAS_NO_HISTORY", "INCOMPLETE_HISTORY", "INCONSISTENT_DATA")
    }
}
