package ru.medovik.hack2026.api.risk.model

import java.time.Instant

enum class RiskLevel { NONE, LOW, MEDIUM, HIGH }
enum class CustomerState { EVALUATED, NO_SERVICES, BLOCKED, ALL_SERVICES_DELETED }

/** All monetary amounts, including exposure and shortfall, are integer eurocents. */
data class FundingSummary(
    val accountId: Long,
    val availableFunds: Long,
    val requiredFunds: Long,
    val shortfall: Long,
    val shortageStrength: Double,
    val renewals: List<RenewalCoverage>,
)

data class RenewalCoverage(
    val serviceId: Long,
    val renewalAt: Instant,
    val price: Long,
    val fundsBefore: Long,
    val covered: Boolean,
    val fundsAfter: Long,
)

data class FeatureContribution(
    val code: String,
    val applicable: Boolean,
    val strength: Double,
    val weight: Double,
    val contribution: Double,
    val explanation: String,
    val evidence: Map<String, String>,
)

data class DataQualityWarning(val code: String, val message: String)

data class ServiceRiskResult(
    val serviceId: Long,
    val ownerId: Long,
    val status: ServiceStatus,
    val score: Double?,
    val level: RiskLevel,
    val loyaltyScore: Double?,
    val rawScore: Double?,
    val features: List<FeatureContribution>,
    val dataQualityWarnings: List<DataQualityWarning>,
    val nextCriticalAt: Instant?,
    val renewalPrice: Long,
    val revenueExposure: Long,
)

data class CustomerExplanation(
    val serviceId: Long,
    val feature: FeatureContribution,
)

data class CustomerRiskResult(
    val accountId: Long,
    val state: CustomerState,
    val attentionScore: Double?,
    val level: RiskLevel,
    val loyaltyScore: Double?,
    val liveServices: Int,
    val servicesAtRisk: Int,
    val portfolioShareAtRisk: Double,
    val nearestRiskAt: Instant?,
    val revenueExposure: Long,
    val funding: FundingSummary,
    val explanations: List<CustomerExplanation>,
)

data class RiskResult(
    val evaluationTime: Instant,
    val settings: RiskSettings,
    val services: List<ServiceRiskResult>,
    val customers: List<CustomerRiskResult>,
    val modelVersion: String = "expert-f1-f5-v1",
)
