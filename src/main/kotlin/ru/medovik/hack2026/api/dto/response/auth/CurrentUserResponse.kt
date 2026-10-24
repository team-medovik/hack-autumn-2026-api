package ru.medovik.hack2026.api.dto.response.auth

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime
import java.util.UUID

@Schema(description = "Публичные данные текущего аутентифицированного сотрудника.")
data class CurrentUserResponse(
    @field:Schema(example = "550e8400-e29b-41d4-a716-446655440000")
    val id: UUID,
    @field:Schema(example = "admin@example.com")
    val email: String,
    @field:Schema(description = "Роль в приложении, заданная при заведении сотрудника.", example = "ADMIN")
    val role: String,
    val createdAt: OffsetDateTime,
)
