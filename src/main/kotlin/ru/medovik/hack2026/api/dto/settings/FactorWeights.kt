package ru.medovik.hack2026.api.dto.settings

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Веса факторов риска: каждое значение от 0 до 100")
data class FactorWeights(
    @field:Schema(description = "Ожидание оплаты", minimum = "0", maximum = "100", example = "70")
    val awaitingPayment: Int,
    @field:Schema(description = "Истечение без автопродления", minimum = "0", maximum = "100", example = "50")
    val expiringWithoutAutoRenewal: Int,
    @field:Schema(description = "Недостаточно средств", minimum = "0", maximum = "100", example = "40")
    val insufficientFunds: Int,
    @field:Schema(description = "Ожидание оплаты без средств", minimum = "0", maximum = "100", example = "30")
    val awaitingPaymentWithoutFunds: Int,
    @field:Schema(description = "Отключение клиентом", minimum = "0", maximum = "100", example = "15")
    val disabledByClient: Int,
    @field:Schema(description = "Бонусов больше денег", minimum = "0", maximum = "100", example = "10")
    val bonusesExceedMoney: Int,
)
