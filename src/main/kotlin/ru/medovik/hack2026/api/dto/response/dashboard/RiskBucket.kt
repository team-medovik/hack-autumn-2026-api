package ru.medovik.hack2026.api.dto.response.dashboard

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Агрегат клиентов одного уровня риска.")
data class RiskBucket(
    @field:Schema(description = "Уровень риска.", allowableValues = ["high", "medium", "low", "none"], example = "high")
    val level: String,
    @field:Schema(description = "Количество клиентов этого уровня.", example = "13", minimum = "0")
    val clients: Int,
    @field:Schema(description = "Ожидаемые ежемесячные потери клиентов уровня. Для none всегда null.", example = "854.50", nullable = true)
    val monthlyLoss: BigDecimal?,
    @field:Schema(description = "Изменилась ли сумма потерь относительно предыдущего снимка.")
    val changed: Boolean,
)
