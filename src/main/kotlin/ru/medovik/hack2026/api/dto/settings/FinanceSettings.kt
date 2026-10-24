package ru.medovik.hack2026.api.dto.settings

data class FinanceSettings(
    val bonusesAsMoney: Boolean,
    val referralBalanceAsMoney: Boolean,
    val balanceLossFactor: Double,
)
