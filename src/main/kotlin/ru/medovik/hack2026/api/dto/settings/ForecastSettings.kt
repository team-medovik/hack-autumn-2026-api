package ru.medovik.hack2026.api.dto.settings

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Горизонт прогноза и интервал обновления")
data class ForecastSettings(
    @field:Schema(description = "Горизонт в днях", minimum = "1", maximum = "30", example = "5")
    val horizonDays: Int,
    @field:Schema(description = "Интервал обновления в минутах", minimum = "1", maximum = "60", example = "5")
    val recalculationIntervalMinutes: Int,
)
