package ru.medovik.hack2026.api.dto.settings

data class PatchRiskThresholds(val medium: Int? = null, val high: Int? = null) {
    fun nonEmpty() = medium != null || high != null
    fun apply(old: RiskThresholds) = old.copy(
        medium = medium ?: old.medium, high = high ?: old.high,
    )
}
