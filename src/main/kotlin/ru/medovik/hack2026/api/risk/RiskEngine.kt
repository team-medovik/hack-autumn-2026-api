package ru.medovik.hack2026.api.risk

import ru.medovik.hack2026.api.risk.feature.AwaitingRenewal
import ru.medovik.hack2026.api.risk.feature.AwaitingRenewalWithoutFunds
import ru.medovik.hack2026.api.risk.feature.ExpiryWithoutAutoRenew
import ru.medovik.hack2026.api.risk.feature.FeatureContext
import ru.medovik.hack2026.api.risk.feature.InsufficientFunds
import ru.medovik.hack2026.api.risk.feature.RiskFeature
import ru.medovik.hack2026.api.risk.feature.SuspendedService
import ru.medovik.hack2026.api.risk.model.DataQualityWarning
import ru.medovik.hack2026.api.risk.model.FeatureContribution
import ru.medovik.hack2026.api.risk.model.FundingSummary
import ru.medovik.hack2026.api.risk.model.RiskResult
import ru.medovik.hack2026.api.risk.model.RiskSettings
import ru.medovik.hack2026.api.risk.model.RiskSnapshot
import ru.medovik.hack2026.api.risk.model.Service
import ru.medovik.hack2026.api.risk.model.ServiceRiskResult
import ru.medovik.hack2026.api.risk.model.ServiceStatus
import java.time.Duration
import java.time.Instant

/** Stateless calculation: no database, Spring context, wall clock or input mutation. */
class RiskEngine(
    private val features: List<RiskFeature> = listOf(
        ExpiryWithoutAutoRenew(), InsufficientFunds(), AwaitingRenewal(),
        AwaitingRenewalWithoutFunds(), SuspendedService(),
    ),
    private val balanceSimulator: BalanceSimulator = BalanceSimulator(),
    private val customerAggregator: CustomerRiskAggregator = CustomerRiskAggregator(),
) {
    init {
        require(features.map { it.code }.distinct().size == features.size) { "Duplicate feature codes" }
    }

    fun calculate(
        snapshot: RiskSnapshot,
        settings: RiskSettings = RiskSettings(),
        evaluationTime: Instant,
    ): RiskResult {
        validate(snapshot)
        require(features.all { it.code in settings.weights }) { "Missing weight for a registered feature" }
        val settingsSnapshot = settings.copy(weights = settings.weights.toMap())
        val horizonEnd = evaluationTime.plus(Duration.ofDays(settings.horizonDays.toLong()))
        val servicesByOwner = snapshot.services.groupBy { it.ownerId }
        val funding = snapshot.accounts.associate { account ->
            account.id to balanceSimulator.calculate(account, servicesByOwner[account.id].orEmpty(), horizonEnd)
        }
        val serviceResults = snapshot.services.sortedBy { it.id }.map { service ->
            score(service, funding.getValue(service.ownerId), settingsSnapshot, evaluationTime)
        }
        val resultsByOwner = serviceResults.groupBy { it.ownerId }
        val customers = snapshot.accounts.sortedBy { it.id }.map { account ->
            customerAggregator.aggregate(
                account.id, resultsByOwner[account.id].orEmpty(), funding.getValue(account.id), settingsSnapshot,
            )
        }
        return RiskResult(evaluationTime, settingsSnapshot, serviceResults, customers)
    }

    private fun score(
        service: Service,
        funding: FundingSummary,
        settings: RiskSettings,
        evaluationTime: Instant,
    ): ServiceRiskResult {
        val context = FeatureContext(service, funding, settings, evaluationTime)
        val contributions = if (!service.status.isLive) emptyList() else features.map { feature ->
            val evaluated = feature.evaluate(context)
            val weight = settings.weights.getValue(feature.code)
            FeatureContribution(
                code = feature.code,
                applicable = evaluated.applicable,
                strength = evaluated.strength,
                weight = weight,
                contribution = weight * evaluated.strength,
                explanation = evaluated.explanation,
                evidence = evaluated.evidence,
            )
        }
        val rawScore = if (service.status.isLive) contributions.sumOf { it.contribution } else null
        val score = rawScore?.coerceAtMost(100.0)
        val nextCriticalAt = when {
            !service.status.isLive -> null
            service.status == ServiceStatus.PROLONG_WAIT -> service.expiresAt.plus(Duration.ofDays(5))
            else -> service.expiresAt
        }
        return ServiceRiskResult(
            serviceId = service.id,
            ownerId = service.ownerId,
            status = service.status,
            score = score,
            level = settings.level(score),
            loyaltyScore = score?.let { 100.0 - it },
            rawScore = rawScore,
            features = contributions,
            dataQualityWarnings = warnings(service, evaluationTime),
            nextCriticalAt = nextCriticalAt,
            renewalPrice = service.price,
            revenueExposure = if (score != null && score >= settings.mediumThreshold && context.inHorizon) {
                service.price
            } else 0L,
        )
    }

    private fun warnings(service: Service, now: Instant): List<DataQualityWarning> = buildList {
        if (service.createdAt.isAfter(now)) {
            add(DataQualityWarning("FUTURE_CREATED_AT", "Услуга создана позже времени расчёта"))
        }
        if (service.status in setOf(ServiceStatus.ACTIVE, ServiceStatus.SUSPENDED) &&
            !service.expiresAt.isAfter(now)
        ) {
            add(DataQualityWarning("EXPIRED_LIVE_SERVICE", "Срок услуги истёк, но статус остаётся ${service.status}"))
        }
        if (service.status == ServiceStatus.PROLONG_WAIT) {
            if (service.expiresAt.isAfter(now)) {
                add(DataQualityWarning("PROLONG_WAIT_BEFORE_EXPIRY", "Услуга ожидает продления до окончания срока"))
            } else if (!now.isBefore(service.expiresAt.plus(Duration.ofDays(5)))) {
                add(DataQualityWarning("PROLONG_WAIT_GRACE_EXCEEDED", "Расчётный пятидневный срок ожидания оплаты истёк"))
            }
        }
    }

    private fun validate(snapshot: RiskSnapshot) {
        require(snapshot.accounts.map { it.id }.distinct().size == snapshot.accounts.size) { "Duplicate account ids" }
        require(snapshot.services.map { it.id }.distinct().size == snapshot.services.size) { "Duplicate service ids" }
        require(snapshot.transactions.map { it.id }.distinct().size == snapshot.transactions.size) { "Duplicate transaction ids" }
        val accounts = snapshot.accounts.map { it.id }.toSet()
        val services = snapshot.services.map { it.id }.toSet()
        require(snapshot.services.all { it.ownerId in accounts }) { "Service references an unknown account" }
        require(snapshot.transactions.all { it.serviceId in services }) { "Transaction references an unknown service" }
    }
}
