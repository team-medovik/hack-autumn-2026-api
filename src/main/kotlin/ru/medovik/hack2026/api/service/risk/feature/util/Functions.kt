package ru.medovik.hack2026.api.service.risk.feature.util

internal fun fundingEvidence(context: FeatureContext): Map<String, String> = with(context.funding) {
    mapOf(
        "availableFunds" to availableFunds.toString(),
        "requiredFunds" to requiredFunds.toString(),
        "shortfall" to shortfall.toString(),
        "currencyUnit" to "EUR_CENT",
    )
}