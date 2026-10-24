package ru.medovik.hack2026.api.dto.settings

data class ModelSettingsValues(
    val factorWeights: FactorWeights,
    val riskThresholds: RiskThresholds,
    val finance: FinanceSettings,
    val forecast: ForecastSettings,
)
