package ru.medovik.hack2026.api.dto.response.client

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Ближайшее событие услуги")
data class ClientServiceEvent(
    @field:Schema(description = "Тип события", allowableValues = ["renew", "expire", "delete"])
    val type: String,
    @field:Schema(description = "Дата события в UTC", example = "2026-10-24")
    val date: LocalDate,
)
