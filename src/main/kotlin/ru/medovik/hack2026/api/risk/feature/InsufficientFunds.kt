package ru.medovik.hack2026.api.risk.feature

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

internal fun fundingEvidence(context: FeatureContext): Map<String, String> = with(context.funding) {
    mapOf(
        "availableFunds" to availableFunds.toString(),
        "requiredFunds" to requiredFunds.toString(),
        "shortfall" to shortfall.toString(),
        "currencyUnit" to "EUR_CENT",
    )
}
