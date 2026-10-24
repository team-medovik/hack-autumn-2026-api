package ru.medovik.hack2026.api.service.risk.feature.util

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
        fun notApplicable(): FeatureEvaluation = FeatureEvaluation(
            applicable = false,
            strength = 0.0,
            explanation = "Условие признака не выполнено",
        )
    }
}
