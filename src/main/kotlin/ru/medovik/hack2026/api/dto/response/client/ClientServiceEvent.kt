package ru.medovik.hack2026.api.dto.response.client

import java.time.LocalDate

data class ClientServiceEvent(
    val type: String,
    val date: LocalDate,
)
