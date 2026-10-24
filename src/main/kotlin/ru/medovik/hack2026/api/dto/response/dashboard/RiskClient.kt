package ru.medovik.hack2026.api.dto.response.dashboard

import java.math.BigDecimal

open class RiskClient(
    val id: String,
    val email: String,
    val score: Double?,
    val level: String,
    val reason: String,
    val monthlyLoss: BigDecimal,
    val lossChanged: Boolean,
)
