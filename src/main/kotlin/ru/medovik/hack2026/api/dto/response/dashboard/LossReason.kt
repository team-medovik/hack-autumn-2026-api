package ru.medovik.hack2026.api.dto.response.dashboard

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Потери, сгруппированные по главной причине риска.")
data class LossReason(
    @field:Schema(description = "Стабильный код причины.", example = "awaiting-payment")
    val id: String,
    @field:Schema(description = "Человекочитаемое название причины.", example = "Ожидает оплаты")
    val label: String,
    @field:Schema(description = "Наивысший уровень риска среди клиентов причины.", allowableValues = ["high", "medium", "low", "none"], example = "high")
    val level: String,
    @field:Schema(description = "Количество клиентов, учтённых по этой причине.", example = "16", minimum = "0")
    val clients: Int,
    @field:Schema(description = "Суммарные ожидаемые ежемесячные потери.", example = "612.40")
    val monthlyLoss: BigDecimal,
)
