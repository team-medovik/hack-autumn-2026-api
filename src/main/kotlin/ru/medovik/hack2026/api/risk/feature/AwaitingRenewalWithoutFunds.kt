package ru.medovik.hack2026.api.risk.feature

import ru.medovik.hack2026.api.risk.model.ServiceStatus

class AwaitingRenewalWithoutFunds : RiskFeature {
    override val code = "F4"

    override fun evaluate(context: FeatureContext): FeatureEvaluation = with(context) {
        if (service.status != ServiceStatus.PROLONG_WAIT) return FeatureEvaluation.notApplicable()
        FeatureEvaluation(
            applicable = true,
            strength = funding.shortageStrength,
            explanation = "Услуга ${service.name} ожидает оплаты; доля нехватки средств " +
                "на продления равна ${funding.shortageStrength}",
            evidence = fundingEvidence(context),
        )
    }
}
