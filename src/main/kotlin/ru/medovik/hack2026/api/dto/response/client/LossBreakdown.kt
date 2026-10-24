package ru.medovik.hack2026.api.dto.response.client

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Расшифровка ожидаемых потерь по услугам")
data class LossBreakdown(
    @field:Schema(description = "Коэффициент потерь из настроек модели", example = "0.5")
    val k: Double,
    @field:Schema(description = "Доля нехватки средств от 0 до 1", example = "0.3")
    val uncoveredShare: Double,
    @field:Schema(description = "Горизонт расчёта в днях", minimum = "1", maximum = "30")
    val horizonDays: Int,
    @field:Schema(description = "Вклад каждой услуги")
    val items: List<LossBreakdownItem>,
)
