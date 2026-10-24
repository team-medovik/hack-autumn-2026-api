package ru.medovik.hack2026.api.service.risk

import ru.medovik.hack2026.api.dto.request.risk.RiskSettings
import ru.medovik.hack2026.api.dto.request.risk.ServiceStatus
import ru.medovik.hack2026.api.dto.response.risk.CustomerExplanation
import ru.medovik.hack2026.api.dto.response.risk.CustomerRiskResult
import ru.medovik.hack2026.api.dto.response.risk.CustomerState
import ru.medovik.hack2026.api.dto.response.risk.FundingSummary
import ru.medovik.hack2026.api.dto.response.risk.ServiceRiskResult

class CustomerRiskAggregator {
    fun aggregate(
        accountId: Long,
        services: List<ServiceRiskResult>,
        funding: FundingSummary,
        settings: RiskSettings,
    ): CustomerRiskResult {
        require(services.all { it.ownerId == accountId } && funding.accountId == accountId) {
            "Results belong to another account"
        }
        val live = services.filter { it.status.isLive }
        val score = live.mapNotNull { it.score }.maxOrNull()
        val atRisk = live.filter { it.score != null && it.score >= settings.mediumThreshold }
        val state = when {
            live.isNotEmpty() -> CustomerState.EVALUATED
            services.isEmpty() -> CustomerState.NO_SERVICES
            services.all { it.status == ServiceStatus.DELETED } -> CustomerState.ALL_SERVICES_DELETED
            else -> CustomerState.BLOCKED
        }
        val explanations = live.flatMap { service ->
            service.features.filter { it.contribution > 0 }.map { CustomerExplanation(service.serviceId, it) }
        }.sortedWith(
            compareByDescending<CustomerExplanation> { it.feature.contribution }
                .thenBy { it.serviceId }.thenBy { it.feature.code },
        )
        return CustomerRiskResult(
            accountId = accountId,
            state = state,
            attentionScore = score,
            level = settings.level(score),
            loyaltyScore = score?.let { 100.0 - it },
            liveServices = live.size,
            servicesAtRisk = atRisk.size,
            portfolioShareAtRisk = if (live.isEmpty()) 0.0 else atRisk.size.toDouble() / live.size,
            nearestRiskAt = atRisk.mapNotNull { it.nextCriticalAt }.minOrNull(),
            revenueExposure = services.fold(0L) { sum, service -> Math.addExact(sum, service.revenueExposure) },
            funding = funding,
            explanations = explanations,
        )
    }
}
