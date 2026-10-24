package ru.medovik.hack2026.api.dto.response.admin

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

@Schema(description = "Созданный сотрудник и временный пароль. Пароль показывается только в этом ответе.")
data class CreatedUserResponse(
    @field:Schema(description = "UUID созданного сотрудника")
    val id: UUID,
    @field:Schema(description = "Логин сотрудника", example = "analyst")
    val login: String,
    @field:Schema(description = "Роль сотрудника", example = "EMPLOYEE")
    val role: String,
    @field:Schema(description = "Статус учётной записи", example = "ACTIVE")
    val status: String,
    @field:Schema(description = "Время создания с часовым поясом")
    val createdAt: OffsetDateTime,
    @field:Schema(description = "Временный пароль, выдаётся один раз", accessMode = Schema.AccessMode.READ_ONLY)
    val temporaryPassword: String,
)
