package ru.medovik.hack2026.api.dto.response.dashboard

import java.math.BigDecimal

data class RiskBucket(
    val level: String,
    val clients: Int,
    val monthlyLoss: BigDecimal?,
    val changed: Boolean,
)
