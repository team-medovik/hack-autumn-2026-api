package ru.medovik.hack2026.api.service.ml

import org.springframework.stereotype.Service
import ru.medovik.hack2026.api.dto.request.risk.PaymentTerm
import ru.medovik.hack2026.api.dto.request.risk.RiskSnapshot
import ru.medovik.hack2026.api.dto.request.risk.ServiceStatus
import ru.medovik.hack2026.api.dto.response.risk.RiskResult
import ru.medovik.hack2026.api.properties.MlProperties
import ru.medovik.hack2026.api.repository.dashboard.StoredBillingSnapshot
import ru.medovik.hack2026.api.service.risk.HybridRiskAdjuster
import java.time.Duration
import java.util.Locale

data class MlAssessment(val result: RiskResult, val annotations: Map<Long, String>)

@Service
class MlAssessmentService(
    private val properties: MlProperties,
    private val histories: MlHistoryRepository,
    private val predictions: MlPredictionService,
    private val audit: MlAuditDispatcher,
) {
    private val adjuster = HybridRiskAdjuster()

    fun assess(stored: StoredBillingSnapshot, payload: RiskSnapshot, base: RiskResult): MlAssessment {
        if (!properties.enabled) return MlAssessment(base, emptyMap())
        val results = base.services.associateBy { it.serviceId }
        val eligible = payload.services.filter { service ->
            val scored = results.getValue(service.id)
            scored.score != null && scored.dataQualityWarnings.isEmpty() &&
                service.status in setOf(ServiceStatus.ACTIVE, ServiceStatus.SUSPENDED) &&
                service.paymentTerm in setOf(PaymentTerm.HOUR, PaymentTerm.DAY, PaymentTerm.MONTH) &&
                service.expiresAt > base.evaluationTime && service.expiresAt <= base.evaluationTime.plus(Duration.ofDays(7))
        }
        var fallback: String? = null
        val batch = if (eligible.isEmpty()) MlPredictionBatch(emptyMap()) else {
            val history = if (stored.live) runCatching { histories.read(stored, properties.maxHistoryEvents) }.getOrNull() else null
            if (history == null) {
                fallback = if (stored.live) "INCOMPLETE_HISTORY" else "SNAPSHOT_HAS_NO_HISTORY"
                MlPredictionBatch.fallback(eligible, fallback)
            } else predictions.predict(stored.id, base.evaluationTime, eligible, history)
        }
        val final = adjuster.apply(base, payload, batch.probabilities)
        val adjusted = final.services.associateBy { it.serviceId }
        val annotations = eligible.associate { service ->
            val original = results.getValue(service.id).score!!
            val current = adjusted.getValue(service.id).score!!
            val outcome = batch.outcomes[service.id]
            service.id to if (outcome?.probability != null) {
                String.format(Locale.ROOT, "Алгоритм %.1f; поправка ML %+.1f; итог %.1f балла", original, current - original, current)
            } else "Оценка алгоритма; ML не применён (${outcome?.reason ?: "NOT_APPLICABLE"})"
        }
        audit.enqueue(stored.id, base, final, batch, fallback)
        return MlAssessment(final, annotations)
    }
}
