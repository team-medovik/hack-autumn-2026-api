package ru.medovik.hack2026.api.dto.settings

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "Режим даты расчёта. В current используется момент запроса, в fixed — fixedAt.")
data class CalculationDate(
    @field:Schema(description = "Режим расчёта", allowableValues = ["current", "fixed"], example = "fixed")
    val mode: String,
    @field:Schema(
        description = "Последний зафиксированный момент в UTC. Обязателен для fixed; " +
            "в current может оставаться прежним или быть null.",
        example = "2026-10-19T15:00:00Z",
        nullable = true,
    )
    val fixedAt: OffsetDateTime?,
)
