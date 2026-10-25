package ru.medovik.hack2026.api.service.risk.feature

import ru.medovik.hack2026.api.dto.settings.FeatureDefinition

data class RegisteredRiskFeature(
    val feature: RiskFeature,
    val definition: FeatureDefinition,
    val defaultWeight: Int,
)
