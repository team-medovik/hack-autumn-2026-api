package ru.medovik.hack2026.api.dto.response.dashboard

import java.math.BigDecimal

class TopRiskClient(
    id: String,
    email: String,
    score: Double?,
    level: String,
    reason: String,
    monthlyLoss: BigDecimal,
    lossChanged: Boolean,
    val factors: List<ScoreFactor>,
    val balance: ClientBalance,
) : RiskClient(
    id = id,
    email = email,
    score = score,
    level = level,
    reason = reason,
    monthlyLoss = monthlyLoss,
    lossChanged = lossChanged,
)
