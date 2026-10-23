package ru.medovik.hack2026.api.dto

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "Стандартный формат ошибки API.")
data class ApiErrorResponse(
    val status: Int,
    @field:Schema(example = "INVALID_CREDENTIALS")
    val error: String,
    @field:Schema(example = "Неверный email или пароль")
    val message: String,
    @field:Schema(example = "/api/v1/auth/login")
    val path: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
)
