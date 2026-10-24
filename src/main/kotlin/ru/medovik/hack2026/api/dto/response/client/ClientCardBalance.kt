package ru.medovik.hack2026.api.dto.response.client

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Денежная позиция клиента в евро")
data class ClientCardBalance(
    @field:Schema(description = "Доступные средства", example = "120.00")
    val available: BigDecimal,
    @field:Schema(description = "Требуется для продления или null, если расчёт не нужен", nullable = true)
    val required: BigDecimal?,
    @field:Schema(description = "Ожидаемые ежемесячные потери", example = "35.50")
    val monthlyLoss: BigDecimal,
    @field:Schema(description = "Бонусный баланс в евро")
    val bonus: BigDecimal,
    @field:Schema(description = "Реферальный баланс в евро")
    val referral: BigDecimal,
)
