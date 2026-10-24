package ru.medovik.hack2026.api.risk.model

data class RiskSettings(
    val horizonDays: Int = 7,
    val weights: Map<String, Double> = mapOf(
        "F1" to 50.0,
        "F2" to 40.0,
        "F3" to 70.0,
        "F4" to 30.0,
        "F5" to 15.0,
    ),
    val mediumThreshold: Double = 40.0,
    val highThreshold: Double = 70.0,
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
    }

    fun level(score: Double?): RiskLevel = when {
        score == null -> RiskLevel.NONE
        score >= highThreshold -> RiskLevel.HIGH
        score >= mediumThreshold -> RiskLevel.MEDIUM
        else -> RiskLevel.LOW
    }
}
