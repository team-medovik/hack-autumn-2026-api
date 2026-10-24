package ru.medovik.hack2026.api.dto.response.risk

data class FeatureContribution(
    val code: String,
    val applicable: Boolean,
    val strength: Double,
    val weight: Double,
    val contribution: Double,
    val explanation: String,
    val evidence: Map<String, String>,
)
