package ru.medovik.hack2026.api.dto.settings

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Учёт балансов и коэффициент потерь от 0 до 1 с шагом 0.05")
data class FinanceSettings(
    @field:Schema(description = "Учитывать доступные бонусы как деньги", example = "true")
    val bonusesAsMoney: Boolean,
    @field:Schema(description = "Учитывать реферальный баланс как деньги", example = "false")
    val referralBalanceAsMoney: Boolean,
    @field:Schema(description = "Коэффициент потерь", minimum = "0", maximum = "1", example = "0.5")
    val balanceLossFactor: Double,
)
