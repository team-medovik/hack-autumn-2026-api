package ru.medovik.hack2026.api.dto.response.dashboard

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Динамика удалений и прогноз непродлений услуг.")
data class ChurnForecast(
    @field:Schema(description = "Количество услуг с риском прямо сейчас.", example = "58", minimum = "0")
    val atRiskServices: Int,
    @field:Schema(description = "Недели по возрастанию даты: восемь недель факта, затем четыре недели прогноза. Пустой массив означает, что снимок пока один.")
    val weeks: List<ChurnWeek>,
)
