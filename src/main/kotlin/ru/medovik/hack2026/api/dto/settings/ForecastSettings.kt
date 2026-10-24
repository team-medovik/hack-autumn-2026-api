package ru.medovik.hack2026.api.dto.settings

data class ForecastSettings(
    val horizonDays: Int,
    val recalculationIntervalMinutes: Int,
)
