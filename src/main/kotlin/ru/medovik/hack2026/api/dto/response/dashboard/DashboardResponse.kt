package ru.medovik.hack2026.api.dto.response.dashboard

import java.math.BigDecimal

data class DashboardResponse(
    val snapshot: SnapshotInfo?,
    val monthlyLoss: BigDecimal,
    val riskBuckets: List<RiskBucket>,
    val topRisk: TopRiskClient?,
    val riskQueue: RiskQueue,
    val lossReasons: List<LossReason>,
    val churn: ChurnForecast,
)
