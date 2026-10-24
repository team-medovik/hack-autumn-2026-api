package ru.medovik.hack2026.api.dto.settings

import io.swagger.v3.oas.annotations.media.Schema
import ru.medovik.hack2026.api.exception.InvalidModelSettingsException

@Schema(description = "Атомарный частичный PATCH с проверкой ожидаемой версии")
data class PatchModelSettingsRequest(
    val version: Long,
    val changes: ModelSettingsChanges,
)

data class PatchFactorWeights(
    val awaitingPayment: Int? = null,
    val expiringWithoutAutoRenewal: Int? = null,
    val insufficientFunds: Int? = null,
    val awaitingPaymentWithoutFunds: Int? = null,
    val disabledByClient: Int? = null,
    val bonusesExceedMoney: Int? = null,
) {
    fun nonEmpty() = listOf(
        awaitingPayment, expiringWithoutAutoRenewal, insufficientFunds,
        awaitingPaymentWithoutFunds, disabledByClient, bonusesExceedMoney,
    ).any { it != null }

    fun apply(old: FactorWeights) = old.copy(
        awaitingPayment = awaitingPayment ?: old.awaitingPayment,
        expiringWithoutAutoRenewal = expiringWithoutAutoRenewal ?: old.expiringWithoutAutoRenewal,
        insufficientFunds = insufficientFunds ?: old.insufficientFunds,
        awaitingPaymentWithoutFunds = awaitingPaymentWithoutFunds ?: old.awaitingPaymentWithoutFunds,
        disabledByClient = disabledByClient ?: old.disabledByClient,
        bonusesExceedMoney = bonusesExceedMoney ?: old.bonusesExceedMoney,
    )
}

data class PatchRiskThresholds(val medium: Int? = null, val high: Int? = null) {
    fun nonEmpty() = medium != null || high != null
    fun apply(old: RiskThresholds) = old.copy(
        medium = medium ?: old.medium, high = high ?: old.high,
    )
}

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

data class PatchForecastSettings(
    val horizonDays: Int? = null,
    val recalculationIntervalMinutes: Int? = null,
) {
    fun nonEmpty() = horizonDays != null || recalculationIntervalMinutes != null
    fun apply(old: ForecastSettings) = old.copy(
        horizonDays = horizonDays ?: old.horizonDays,
        recalculationIntervalMinutes = recalculationIntervalMinutes ?: old.recalculationIntervalMinutes,
    )
}

data class ModelSettingsChanges(
    val factorWeights: PatchFactorWeights? = null,
    val riskThresholds: PatchRiskThresholds? = null,
    val finance: PatchFinanceSettings? = null,
    val forecast: PatchForecastSettings? = null,
) {
    fun mergeInto(old: ModelSettingsValues): ModelSettingsValues {
        val states = listOf(
            factorWeights?.nonEmpty(), riskThresholds?.nonEmpty(), finance?.nonEmpty(), forecast?.nonEmpty(),
        )
        if (states.none { it == true }) {
            throw InvalidModelSettingsException("changes должен содержать хотя бы одно изменяемое поле")
        }
        if (states.any { it == false }) {
            throw InvalidModelSettingsException("Группы изменений не должны быть пустыми")
        }
        return old.copy(
            factorWeights = factorWeights?.apply(old.factorWeights) ?: old.factorWeights,
            riskThresholds = riskThresholds?.apply(old.riskThresholds) ?: old.riskThresholds,
            finance = finance?.apply(old.finance) ?: old.finance,
            forecast = forecast?.apply(old.forecast) ?: old.forecast,
        )
    }
}
