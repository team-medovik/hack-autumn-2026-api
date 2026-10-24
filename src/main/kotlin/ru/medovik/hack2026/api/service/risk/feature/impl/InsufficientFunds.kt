package ru.medovik.hack2026.api.service.risk.feature.impl

import ru.medovik.hack2026.api.service.risk.feature.RiskFeature
import ru.medovik.hack2026.api.service.risk.feature.util.FeatureContext
import ru.medovik.hack2026.api.service.risk.feature.util.FeatureEvaluation
import ru.medovik.hack2026.api.service.risk.feature.util.fundingEvidence

class InsufficientFunds : RiskFeature {
    override val code = "F2"

    override fun evaluate(context: FeatureContext): FeatureEvaluation = with(context) {
        if (!service.status.isLive || !inHorizon) return FeatureEvaluation.notApplicable()
        val renewal = renewalCoverage ?: return FeatureEvaluation.notApplicable()
        FeatureEvaluation(
            applicable = true,
            strength = renewal.shortageStrength,
            explanation = "Перед продлением услуги ${service.name} доступно ${renewal.fundsBefore} евроцентов; " +
                    "цена продления ${renewal.price}, не хватает ${renewal.shortfall} евроцентов",
            evidence = fundingEvidence(context),
        )
    }
}
