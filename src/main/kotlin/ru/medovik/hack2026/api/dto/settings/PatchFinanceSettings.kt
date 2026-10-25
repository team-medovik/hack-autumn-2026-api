package ru.medovik.hack2026.api.dto.settings

data class PatchFinanceSettings(
    val bonusesAsMoney: Boolean? = null,
    val referralBalanceAsMoney: Boolean? = null,
    val balanceLossFactor: Double? = null,
) {
    fun nonEmpty() = bonusesAsMoney != null || referralBalanceAsMoney != null || balanceLossFactor != null
    fun apply(old: FinanceSettings) = old.copy(
        bonusesAsMoney = bonusesAsMoney ?: old.bonusesAsMoney,
        referralBalanceAsMoney = referralBalanceAsMoney ?: old.referralBalanceAsMoney,
        balanceLossFactor = balanceLossFactor ?: old.balanceLossFactor,
    )
}
