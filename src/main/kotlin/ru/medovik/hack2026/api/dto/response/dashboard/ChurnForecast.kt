package ru.medovik.hack2026.api.dto.response.dashboard

data class ChurnForecast(
    val atRiskServices: Int,
    val weeks: List<ChurnWeek>,
)
