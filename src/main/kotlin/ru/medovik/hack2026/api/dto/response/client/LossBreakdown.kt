package ru.medovik.hack2026.api.dto.response.client

data class LossBreakdown(
    val k: Double,
    val uncoveredShare: Double,
    val items: List<LossBreakdownItem>,
)
