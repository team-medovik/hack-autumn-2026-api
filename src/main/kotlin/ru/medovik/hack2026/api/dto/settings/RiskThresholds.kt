package ru.medovik.hack2026.api.dto.settings

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Границы уровней риска: 0 < medium < high ≤ 100")
data class RiskThresholds(
    @field:Schema(description = "Минимальный score среднего риска", minimum = "1", maximum = "99", example = "40")
    val medium: Int,
    @field:Schema(description = "Минимальный score высокого риска", minimum = "2", maximum = "100", example = "70")
    val high: Int,
)
