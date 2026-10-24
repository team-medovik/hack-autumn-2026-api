package ru.medovik.hack2026.api.dto.settings

data class FactorWeights(
    val awaitingPayment: Int,
    val expiringWithoutAutoRenewal: Int,
    val insufficientFunds: Int,
    val awaitingPaymentWithoutFunds: Int,
    val disabledByClient: Int,
    val bonusesExceedMoney: Int,
)
