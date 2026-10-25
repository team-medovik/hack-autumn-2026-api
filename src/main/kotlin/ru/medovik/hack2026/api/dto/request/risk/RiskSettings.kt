package ru.medovik.hack2026.api.dto.request.risk

import ru.medovik.hack2026.api.dto.response.risk.RiskLevel
import ru.medovik.hack2026.api.service.risk.feature.RiskFeatureRegistry

data class RiskSettings(
    val horizonDays: Int = 7,
    val weights: Map<String, Double> = RiskFeatureRegistry.entries.associate {
        it.feature.code to it.defaultWeight.toDouble()
    },
    val mediumThreshold: Double = 40.0,
    val highThreshold: Double = 70.0,
    val bonusesAsMoney: Boolean = true,
    val referralBalanceAsMoney: Boolean = false,
    val balanceLossFactor: Double = 0.5,
) {
    init {
        require(horizonDays > 0) { "horizonDays must be positive" }
        require(weights.keys.all { it.isNotBlank() }) { "Feature codes must not be blank" }
        require(weights.values.all { it.isFinite() && it in 0.0..100.0 }) {
            "Weights must be finite and in 0..100"
        }
        require(mediumThreshold.isFinite() && highThreshold.isFinite() &&
            mediumThreshold > 0 && mediumThreshold < highThreshold && highThreshold <= 100
        ) { "Thresholds must satisfy 0 < medium < high <= 100" }
        require(balanceLossFactor.isFinite() && balanceLossFactor in 0.0..1.0) {
            "balanceLossFactor must be in 0..1"
        }
    }

    fun level(score: Double?): RiskLevel = when {
        score == null -> RiskLevel.NONE
        score >= highThreshold -> RiskLevel.HIGH
        score >= mediumThreshold -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }
}
