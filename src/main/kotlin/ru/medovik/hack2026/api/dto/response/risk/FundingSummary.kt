package ru.medovik.hack2026.api.dto.response.risk

/** All monetary amounts, including exposure and shortfall, are integer eurocents. */
data class FundingSummary(
    val accountId: Long,
    val availableFunds: Long,
    val requiredFunds: Long,
    val shortfall: Long,
    val shortageStrength: Double,
    val renewals: List<RenewalCoverage>,
)
