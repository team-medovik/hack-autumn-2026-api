package ru.medovik.hack2026.api.service.risk.feature.util

internal fun fundingEvidence(context: FeatureContext): Map<String, String> = with(context.funding) {
    val renewal = requireNotNull(context.renewalCoverage)
    mapOf(
        "availableFunds" to availableFunds.toString(),
        "requiredFunds" to requiredFunds.toString(),
        "shortfall" to shortfall.toString(),
        "serviceId" to renewal.serviceId.toString(),
        "renewalPrice" to renewal.price.toString(),
        "fundsBefore" to renewal.fundsBefore.toString(),
        "fundsAfter" to renewal.fundsAfter.toString(),
        "covered" to renewal.covered.toString(),
        "serviceShortfall" to renewal.shortfall.toString(),
        "serviceShortageStrength" to renewal.shortageStrength.toString(),
        "currencyUnit" to "EUR_CENT",
    )
}
