package ru.medovik.hack2026.api.service.risk.feature

import ru.medovik.hack2026.api.service.risk.feature.util.FeatureContext
import ru.medovik.hack2026.api.service.risk.feature.util.FeatureEvaluation

interface RiskFeature {
    val code: String

    fun evaluate(context: FeatureContext): FeatureEvaluation
}
