package ru.medovik.hack2026.api.dto.response.client

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Фактор, повлиявший на оценку клиента")
data class ClientFactor(
    @field:Schema(description = "Стабильный код фактора", example = "insufficient_funds")
    val id: String,
    @field:Schema(description = "Пояснение для пользователя")
    val label: String,
    @field:Schema(description = "Вклад фактора в score", minimum = "0", example = "40")
    val points: Double,
)
