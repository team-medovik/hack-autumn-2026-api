package ru.medovik.hack2026.api.risk.feature

import ru.medovik.hack2026.api.risk.model.FundingSummary
import ru.medovik.hack2026.api.risk.model.RiskSettings
import ru.medovik.hack2026.api.risk.model.Service
import java.time.Duration
import java.time.Instant

data class FeatureContext(
    val service: Service,
    val funding: FundingSummary,
    val settings: RiskSettings,
    val evaluationTime: Instant,
) {
    private val timeToExpiry = Duration.between(evaluationTime, service.expiresAt)
    val daysToExpiry: Double = (timeToExpiry.seconds + timeToExpiry.nano / 1_000_000_000.0) / 86_400.0
    val inHorizon: Boolean = daysToExpiry <= settings.horizonDays
}

data class FeatureEvaluation(
    val applicable: Boolean,
    val strength: Double,
    val explanation: String,
    val evidence: Map<String, String> = emptyMap(),
) {
    init {
        require(strength.isFinite() && strength in 0.0..1.0) { "Feature strength must be in 0..1" }
        require(applicable || strength == 0.0) { "An inapplicable feature must have zero strength" }
    }

    companion object {
        fun notApplicable() = FeatureEvaluation(false, 0.0, "Условие признака не выполнено")
    }
}

interface RiskFeature {
    val code: String
    fun evaluate(context: FeatureContext): FeatureEvaluation
}
