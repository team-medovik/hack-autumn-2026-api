package ru.medovik.hack2026.api.dto.response.dashboard

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Фактор, сформировавший оценку риска клиента.")
data class ScoreFactor(
    @field:Schema(description = "Стабильный код фактора.", example = "awaiting-payment")
    val id: String,
    @field:Schema(description = "Готовое к показу название фактора.", example = "Ожидает оплаты")
    val label: String,
    @field:Schema(description = "Вклад фактора в оценку риска.", example = "70", minimum = "0", maximum = "100")
    val points: Int,
)
