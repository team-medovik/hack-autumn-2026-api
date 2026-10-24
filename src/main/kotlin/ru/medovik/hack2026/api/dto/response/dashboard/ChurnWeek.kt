package ru.medovik.hack2026.api.dto.response.dashboard

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDate

@Schema(description = "Показатель оттока услуг за одну календарную неделю.")
data class ChurnWeek(
    @field:Schema(description = "Понедельник недели в ISO 8601.", example = "2026-10-26")
    val weekStart: LocalDate,
    @field:Schema(description = "Источник значения: факт удалений или прогноз непродлений.", allowableValues = ["fact", "forecast"], example = "forecast")
    val kind: String,
    @field:Schema(description = "Количество услуг.", example = "18", minimum = "0")
    val services: Int,
)
