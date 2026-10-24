package ru.medovik.hack2026.api.service.risk.feature.util

import ru.medovik.hack2026.api.dto.request.risk.RiskSettings
import ru.medovik.hack2026.api.dto.request.risk.Service
import ru.medovik.hack2026.api.dto.request.risk.Account
import ru.medovik.hack2026.api.dto.response.risk.FundingSummary
import java.time.Duration
import java.time.Instant

data class FeatureContext(
    val account: Account,
    val service: Service,
    val funding: FundingSummary,
    val settings: RiskSettings,
    val evaluationTime: Instant,
) {
    private val timeToExpiry = Duration.between(evaluationTime, service.expiresAt)

    val daysToExpiry: Double =
        (timeToExpiry.seconds + timeToExpiry.nano / NANOSECONDS_PER_SECOND) / SECONDS_PER_DAY

    val inHorizon: Boolean = daysToExpiry <= settings.horizonDays

    private companion object {
        const val NANOSECONDS_PER_SECOND = 1_000_000_000.0
        const val SECONDS_PER_DAY = 86_400.0
    }
}
