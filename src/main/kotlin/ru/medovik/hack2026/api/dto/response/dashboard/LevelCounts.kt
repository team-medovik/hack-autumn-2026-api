package ru.medovik.hack2026.api.dto.response.dashboard

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Количество клиентов по уровням риска во всём последнем снимке.")
data class LevelCounts(
    @field:Schema(description = "Количество клиентов высокого риска.", example = "13", minimum = "0")
    val high: Int,
    @field:Schema(description = "Количество клиентов среднего риска.", example = "14", minimum = "0")
    val medium: Int,
    @field:Schema(description = "Количество клиентов низкого риска.", example = "9", minimum = "0")
    val low: Int,
    @field:Schema(description = "Количество клиентов без оценки.", example = "6", minimum = "0")
    val none: Int,
)
