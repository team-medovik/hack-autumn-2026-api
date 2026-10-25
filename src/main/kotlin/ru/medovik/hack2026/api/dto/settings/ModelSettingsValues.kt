package ru.medovik.hack2026.api.dto.settings

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Все группы параметров модели риска")
data class ModelSettingsValues(
    @field:Schema(description = "Веса факторов риска")
    val factorWeights: Map<String, Int>,
    @field:Schema(description = "Пороги среднего и высокого риска")
    val riskThresholds: RiskThresholds,
    @field:Schema(description = "Правила учёта денег")
    val finance: FinanceSettings,
    @field:Schema(description = "Параметры прогноза")
    val forecast: ForecastSettings,
)
