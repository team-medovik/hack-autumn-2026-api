package ru.medovik.hack2026.api.dto.response.risk

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.Instant

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
    val potentialMonthlyLoss: Long,
    @get:JsonIgnore
    val horizonPaymentExposure: Long,
    val funding: FundingSummary,
    val explanations: List<CustomerExplanation>,
)
