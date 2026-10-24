package ru.medovik.hack2026.api.dto.response.common

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "Стандартный формат ошибки API.")
data class ApiErrorResponse(
    @field:Schema(description = "HTTP-статус ответа.", example = "401")
    val status: Int,
    @field:Schema(description = "Стабильный машинный код ошибки.", example = "INVALID_CREDENTIALS")
    val error: String,
    @field:Schema(description = "Человекочитаемое описание ошибки.", example = "Неверный email или пароль")
    val message: String,
    @field:Schema(description = "Путь запроса, на котором произошла ошибка.", example = "/api/v1/auth/login")
    val path: String,
    @field:Schema(description = "Время формирования ошибки в ISO 8601 с часовым поясом.", example = "2026-10-24T18:30:00+03:00")
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
)
