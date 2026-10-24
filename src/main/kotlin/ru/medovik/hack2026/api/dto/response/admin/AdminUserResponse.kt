package ru.medovik.hack2026.api.dto.response.admin

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

@Schema(description = "Учётная запись сотрудника в административном разделе.")
data class AdminUserResponse(
    @field:Schema(description = "UUID сотрудника")
    val id: UUID,
    @field:Schema(description = "Логин сотрудника", example = "employee")
    val login: String,
    @field:Schema(description = "Роль в приложении", example = "EMPLOYEE")
    val role: String,
    @field:Schema(description = "Статус учётной записи", example = "ACTIVE")
    val status: String,
    @field:Schema(description = "Время создания с часовым поясом")
    val createdAt: OffsetDateTime,
)
