package ru.medovik.hack2026.api.dto.response.risk

import ru.medovik.hack2026.api.dto.request.risk.RiskSettings
import java.time.Instant

data class RiskResult(
    val evaluationTime: Instant,
    val settings: RiskSettings,
    val services: List<ServiceRiskResult>,
    val customers: List<CustomerRiskResult>,
    val modelVersion: String = "expert-f1-f5-v1",
)
