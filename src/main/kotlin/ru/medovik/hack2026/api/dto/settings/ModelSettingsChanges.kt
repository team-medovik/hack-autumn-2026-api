package ru.medovik.hack2026.api.dto.settings

import ru.medovik.hack2026.api.exception.InvalidModelSettingsException

data class ModelSettingsChanges(
    val factorWeights: Map<String, Int>? = null,
    val riskThresholds: PatchRiskThresholds? = null,
    val finance: PatchFinanceSettings? = null,
    val forecast: PatchForecastSettings? = null,
) {
    fun mergeInto(old: ModelSettingsValues): ModelSettingsValues {
        if (factorWeights == null && riskThresholds == null && finance == null && forecast == null) {
            throw InvalidModelSettingsException("changes должен содержать хотя бы одну группу")
        }
        // Controllers enforce minProperties. Extra JSON keys in non-weight groups are permitted by OpenAPI.
        return old.copy(
            factorWeights = factorWeights?.let { old.factorWeights + it } ?: old.factorWeights,
            riskThresholds = riskThresholds?.apply(old.riskThresholds) ?: old.riskThresholds,
            finance = finance?.apply(old.finance) ?: old.finance,
            forecast = forecast?.apply(old.forecast) ?: old.forecast,
        )
    }
}
