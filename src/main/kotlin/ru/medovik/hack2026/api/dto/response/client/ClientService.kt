package ru.medovik.hack2026.api.dto.response.client

import java.math.BigDecimal

data class ClientService(
    val id: String,
    val name: String,
    val score: Double?,
    val level: String,
    val status: String,
    val monthlyPrice: BigDecimal?,
    val event: ClientServiceEvent?,
    val autoRenew: Boolean?,
)
