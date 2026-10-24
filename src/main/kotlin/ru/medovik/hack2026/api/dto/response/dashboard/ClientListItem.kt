package ru.medovik.hack2026.api.dto.response.dashboard

import java.math.BigDecimal

data class ClientListItem(
    val id: String,
    val email: String,
    val score: Double?,
    val level: String,
    val reason: String?,
    val balance: BigDecimal,
    val topUp: BigDecimal,
    val monthlyLoss: BigDecimal,
)
