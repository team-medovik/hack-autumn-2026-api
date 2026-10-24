package ru.medovik.hack2026.api.service.risk

import ru.medovik.hack2026.api.dto.request.risk.Account
import ru.medovik.hack2026.api.dto.request.risk.BonusState
import ru.medovik.hack2026.api.dto.request.risk.Service
import ru.medovik.hack2026.api.dto.request.risk.RiskSettings
import ru.medovik.hack2026.api.dto.response.risk.FundingSummary
import ru.medovik.hack2026.api.dto.response.risk.RenewalCoverage
import java.time.Instant

class BalanceSimulator {
    /**
     * The F1..F5 specification includes ALL live renewals within the horizon,
     * regardless of autoProlong. Each service contributes one renewal price.
     * Chronological coverage is explanatory; F2/F4 use the shared shortage ratio.
     */
    fun calculate(
        account: Account,
        services: List<Service>,
        horizonEnd: Instant,
        settings: RiskSettings = RiskSettings(),
    ): FundingSummary {
        require(services.all { it.ownerId == account.id }) { "Services belong to another account" }
        val available = Math.addExact(
            Math.addExact(
                account.balance,
                if (settings.bonusesAsMoney && account.bonusState == BonusState.UNLOCKED) account.bonusBalance else 0L,
            ),
            if (settings.referralBalanceAsMoney) account.withdrawBalance else 0L,
        )
        val renewals = services.filter { it.status.isLive && !it.expiresAt.isAfter(horizonEnd) }
            .sortedWith(compareBy<Service> { it.expiresAt }.thenBy { it.id })
        val required = renewals.fold(0L) { sum, service -> Math.addExact(sum, service.price) }
        var remaining = available
        val coverage = renewals.map { service ->
            val before = remaining
            val covered = remaining >= service.price
            // An unsuccessful renewal does not consume the remaining balance.
            if (covered) remaining -= service.price
            RenewalCoverage(service.id, service.expiresAt, service.price, before, covered, remaining)
        }
        val shortfall = (required - available).coerceAtLeast(0L)
        return FundingSummary(
            accountId = account.id,
            availableFunds = available,
            requiredFunds = required,
            shortfall = shortfall,
            shortageStrength = if (required == 0L) 0.0 else shortfall.toDouble() / required.toDouble(),
            renewals = coverage,
        )
    }
}
