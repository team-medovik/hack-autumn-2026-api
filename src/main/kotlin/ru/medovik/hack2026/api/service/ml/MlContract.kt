package ru.medovik.hack2026.api.service.ml

import ru.medovik.hack2026.api.dto.request.risk.Service
import java.time.Instant

const val ML_CONTRACT = "renewal-risk-v1"
const val ML_FEATURES = "event-driven-preexpiry-features-v1"
const val ML_TARGET = "event-renewal-5d-paid-replacement-nonfinancial-excluded-v1"

data class MlHistory(
    val schemaVersion: String = "vps-billing-events-v2",
    val runId: String,
    val asOf: Instant,
    val syntheticOnly: Boolean,
    val events: List<Map<String, Any?>>,
)
data class MlHistoryInput(val kind: String = "history", val history: MlHistory)
data class MlRequest(
    val requestId: String,
    val contractVersion: String = ML_CONTRACT,
    val snapshotRevision: String,
    val predictionAt: Instant,
    val serviceIds: List<Long>,
    val input: MlHistoryInput,
)
data class MlOutcome(
    val probability: Double? = null,
    val reason: String? = null,
)
data class MlPredictionBatch(
    val outcomes: Map<Long, MlOutcome>,
    val modelVersion: String? = null,
) {
    val probabilities: Map<Long, Double> get() = outcomes.mapNotNull { (id, outcome) ->
        outcome.probability?.let { id to it }
    }.toMap()
    companion object {
        fun fallback(services: List<Service>, reason: String) =
            MlPredictionBatch(services.associate { it.id to MlOutcome(reason = reason) })
    }
}
