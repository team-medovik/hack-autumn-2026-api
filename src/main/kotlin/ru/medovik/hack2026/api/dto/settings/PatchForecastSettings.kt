package ru.medovik.hack2026.api.dto.settings

data class PatchForecastSettings(
    val horizonDays: Int? = null,
    val recalculationIntervalMinutes: Int? = null,
) {
    fun nonEmpty() = horizonDays != null || recalculationIntervalMinutes != null
    fun apply(old: ForecastSettings) = old.copy(
        horizonDays = horizonDays ?: old.horizonDays,
        recalculationIntervalMinutes = recalculationIntervalMinutes ?: old.recalculationIntervalMinutes,
    )
}
