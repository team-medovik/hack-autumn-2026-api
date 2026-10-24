package ru.medovik.hack2026.api.service.risk.feature.impl

import ru.medovik.hack2026.api.dto.request.risk.ServiceStatus
import ru.medovik.hack2026.api.service.risk.feature.RiskFeature
import ru.medovik.hack2026.api.service.risk.feature.util.FeatureContext
import ru.medovik.hack2026.api.service.risk.feature.util.FeatureEvaluation
import ru.medovik.hack2026.api.service.risk.feature.util.fundingEvidence

class AwaitingRenewalWithoutFunds : RiskFeature {
    override val code = "F4"

    override fun evaluate(context: FeatureContext): FeatureEvaluation = with(context) {
        if (service.status != ServiceStatus.PROLONG_WAIT) return FeatureEvaluation.notApplicable()
        val renewal = renewalCoverage ?: return FeatureEvaluation.notApplicable()
        FeatureEvaluation(
            applicable = true,
            strength = renewal.shortageStrength,
            explanation = "Услуга ${service.name} ожидает оплаты; доля нехватки средств " +
                    "на её продление равна ${renewal.shortageStrength} " +
                    "(${renewal.shortfall} из ${renewal.price} евроцентов)",
            evidence = fundingEvidence(context),
        )
    }
}
