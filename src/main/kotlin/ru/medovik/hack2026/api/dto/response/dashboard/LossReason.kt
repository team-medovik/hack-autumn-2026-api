package ru.medovik.hack2026.api.dto.response.dashboard

import java.math.BigDecimal

data class LossReason(
    val id: String,
    val label: String,
    val level: String,
    val clients: Int,
    val monthlyLoss: BigDecimal,
)
