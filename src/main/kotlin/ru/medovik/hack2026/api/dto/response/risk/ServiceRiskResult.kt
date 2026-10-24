package ru.medovik.hack2026.api.dto.response.risk

import ru.medovik.hack2026.api.dto.request.risk.ServiceStatus
import java.time.Instant

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
