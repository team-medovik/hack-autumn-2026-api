package ru.medovik.hack2026.api.service.risk

import org.slf4j.LoggerFactory
import ru.medovik.hack2026.api.dto.request.risk.RiskSettings
import ru.medovik.hack2026.api.dto.request.risk.Account
import ru.medovik.hack2026.api.dto.request.risk.RiskSnapshot
import ru.medovik.hack2026.api.dto.request.risk.Service
import ru.medovik.hack2026.api.dto.request.risk.ServiceStatus
import ru.medovik.hack2026.api.dto.response.risk.DataQualityWarning
import ru.medovik.hack2026.api.dto.response.risk.FeatureContribution
import ru.medovik.hack2026.api.dto.response.risk.FundingSummary
import ru.medovik.hack2026.api.dto.response.risk.RiskResult
import ru.medovik.hack2026.api.dto.response.risk.ServiceRiskResult
import ru.medovik.hack2026.api.service.risk.feature.util.FeatureContext
import ru.medovik.hack2026.api.service.risk.feature.RiskFeature
import ru.medovik.hack2026.api.service.risk.feature.RiskFeatureRegistry
import java.time.Duration
import java.time.Instant

/** Stateless calculation: no database, wall clock or input mutation. */
class RiskEngine(
    private val features: List<RiskFeature> = RiskFeatureRegistry.entries.map { it.feature },
    private val balanceSimulator: BalanceSimulator = BalanceSimulator(),
    private val customerAggregator: CustomerRiskAggregator = CustomerRiskAggregator(),
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    init {
        require(features.map { it.code }.distinct().size == features.size) { "Duplicate feature codes" }
    }

    fun calculate(
        snapshot: RiskSnapshot,
        settings: RiskSettings = RiskSettings(),
        evaluationTime: Instant,
    ): RiskResult {
        logger.debug("Risk calculation started: accounts={}, services={}, transactions={}, features={}, evaluationTime={}, horizonDays={}",
            snapshot.accounts.size, snapshot.services.size, snapshot.transactions.size,
            features.map { it.code }, evaluationTime, settings.horizonDays)
        validate(snapshot)
        require(features.all { it.code in settings.weights }) { "Missing weight for a registered feature" }
        val settingsSnapshot = settings.copy(weights = settings.weights.toMap())
        val horizonEnd = evaluationTime.plus(Duration.ofDays(settings.horizonDays.toLong()))
        val servicesByOwner = snapshot.services.groupBy { it.ownerId }
        val accountsById = snapshot.accounts.associateBy { it.id }
        val fundingByAccount = snapshot.accounts.associate { account ->
            account.id to balanceSimulator.calculate(
                account = account,
                services = servicesByOwner[account.id].orEmpty(),
                horizonEnd = horizonEnd,
                settings = settingsSnapshot,
            )
        }
        val serviceResults = snapshot.services.sortedBy { it.id }.map { service ->
            score(
                account = accountsById.getValue(service.ownerId),
                service = service,
                funding = fundingByAccount.getValue(service.ownerId),
                settings = settingsSnapshot,
                evaluationTime = evaluationTime,
            )
        }
        val resultsByOwner = serviceResults.groupBy { it.ownerId }
        val customers = snapshot.accounts.sortedBy { it.id }.map { account ->
            customerAggregator.aggregate(
                accountId = account.id,
                services = resultsByOwner[account.id].orEmpty(),
                funding = fundingByAccount.getValue(account.id),
                settings = settingsSnapshot,
            )
        }
        logger.debug("Risk calculation completed: accounts={}, services={}, scoredServices={}, scoredCustomers={}",
            customers.size, serviceResults.size, serviceResults.count { it.score != null },
            customers.count { it.attentionScore != null })
        return RiskResult(
            evaluationTime = evaluationTime,
            settings = settingsSnapshot,
            services = serviceResults,
            customers = customers,
        )
    }

    private fun score(
        account: Account,
        service: Service,
        funding: FundingSummary,
        settings: RiskSettings,
        evaluationTime: Instant,
    ): ServiceRiskResult {
        val context = FeatureContext(
            account = account,
            service = service,
            funding = funding,
            settings = settings,
            evaluationTime = evaluationTime,
        )
        val contributions = if (!service.status.isLive) emptyList() else features.map { feature ->
            val evaluated = feature.evaluate(context)
            val weight = settings.weights.getValue(feature.code)
            if (logger.isTraceEnabled) {
                logger.trace("Risk feature evaluated: serviceId={}, featureCode={}, applicable={}, strength={}, weight={}, contribution={}",
                    service.id, feature.code, evaluated.applicable, evaluated.strength, weight,
                    weight * evaluated.strength)
            }
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
            service.status == ServiceStatus.PROLONG_WAIT -> service.expiresAt.plus(GRACE_PERIOD)
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
            } else if (!now.isBefore(service.expiresAt.plus(GRACE_PERIOD))) {
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

    private companion object {
        val GRACE_PERIOD: Duration = Duration.ofDays(5)
    }
}
