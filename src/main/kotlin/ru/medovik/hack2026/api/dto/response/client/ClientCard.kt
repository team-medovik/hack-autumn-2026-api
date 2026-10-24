package ru.medovik.hack2026.api.dto.response.client

data class ClientCard(
    val id: String,
    val email: String,
    val score: Double?,
    val level: String,
    val factors: List<ClientFactor>,
    val balance: ClientCardBalance,
    val services: List<ClientService>,
    val lossBreakdown: LossBreakdown,
)
