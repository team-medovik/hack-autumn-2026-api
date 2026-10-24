package ru.medovik.hack2026.api.dto.response.dashboard

data class RiskQueue(
    val total: Int,
    val items: List<RiskClient>,
)
