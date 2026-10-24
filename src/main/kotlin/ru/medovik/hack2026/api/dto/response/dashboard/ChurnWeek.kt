package ru.medovik.hack2026.api.dto.response.dashboard

import java.time.LocalDate

data class ChurnWeek(
    val weekStart: LocalDate,
    val kind: String,
    val services: Int,
)
