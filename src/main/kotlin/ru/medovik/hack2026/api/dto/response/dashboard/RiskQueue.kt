package ru.medovik.hack2026.api.dto.response.dashboard

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Короткая очередь клиентов, требующих внимания.")
data class RiskQueue(
    @field:Schema(description = "Общее количество клиентов в последнем снимке.", example = "42", minimum = "0")
    val total: Int,
    @field:Schema(description = "Не более пяти клиентов с наибольшей оценкой риска.")
    val items: List<RiskClient>,
)
