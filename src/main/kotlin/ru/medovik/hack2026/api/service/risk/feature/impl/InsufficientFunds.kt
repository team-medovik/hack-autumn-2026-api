package ru.medovik.hack2026.api.service.risk.feature.impl

import ru.medovik.hack2026.api.service.risk.feature.RiskFeature
import ru.medovik.hack2026.api.service.risk.feature.util.FeatureContext
import ru.medovik.hack2026.api.service.risk.feature.util.FeatureEvaluation
import ru.medovik.hack2026.api.service.risk.feature.util.fundingEvidence

class InsufficientFunds : RiskFeature {
    override val code = "F2"

    override fun evaluate(context: FeatureContext): FeatureEvaluation = with(context) {
        if (!service.status.isLive || !inHorizon) return FeatureEvaluation.notApplicable()
        FeatureEvaluation(
            applicable = true,
            strength = funding.shortageStrength,
            explanation = "Доступно ${funding.availableFunds} евроцентов; на продления в горизонте " +
                    "${settings.horizonDays} дн. нужно ${funding.requiredFunds} евроцентов",
            evidence = fundingEvidence(context),
        )
    }
}
