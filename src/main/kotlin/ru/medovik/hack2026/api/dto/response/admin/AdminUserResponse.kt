package ru.medovik.hack2026.api.dto.response.admin

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

@Schema(description = "Учётная запись сотрудника в административном разделе.")
data class AdminUserResponse(
    val id: UUID,
    @field:Schema(example = "employee@example.com")
    val email: String,
    @field:Schema(example = "EMPLOYEE")
    val role: String,
    @field:Schema(example = "ACTIVE")
    val status: String,
    val createdAt: OffsetDateTime,
)
