package ru.medovik.hack2026.api.dto.response.dashboard

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Клиент в очереди внимания.")
open class RiskClient(
    @field:Schema(description = "ID клиента в биллинге.", example = "1321245")
    val id: String,
    @field:Schema(description = "Email клиента.", example = "client5@mail.ru")
    val email: String,
    @field:Schema(description = "Оценка риска от 0 до 100. null, если оценка не рассчитывалась.", example = "94.2", minimum = "0", maximum = "100", nullable = true)
    val score: Double?,
    @field:Schema(description = "Уровень риска.", allowableValues = ["high", "medium", "low", "none"], example = "high")
    val level: String,
    @field:Schema(description = "Главная человекочитаемая причина риска.", example = "Ожидает оплаты")
    val reason: String,
    @field:Schema(description = "Ожидаемая ежемесячная потеря, если клиент уйдёт.", example = "14.40")
    val monthlyLoss: BigDecimal,
    @field:Schema(description = "Изменилась ли потеря относительно предыдущего снимка.")
    val lossChanged: Boolean,
)
