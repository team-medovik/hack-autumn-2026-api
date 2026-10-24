package ru.medovik.hack2026.api.dto.response.admin

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

@Schema(description = "Созданный сотрудник и временный пароль. Пароль показывается только в этом ответе.")
data class CreatedUserResponse(
    val id: UUID,
    val email: String,
    val role: String,
    val status: String,
    val createdAt: OffsetDateTime,
    @field:Schema(description = "Временный пароль, выдаётся один раз.")
    val temporaryPassword: String,
)
