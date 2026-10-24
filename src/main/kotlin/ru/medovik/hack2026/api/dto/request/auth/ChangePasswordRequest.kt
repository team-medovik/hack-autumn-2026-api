package ru.medovik.hack2026.api.dto.request.auth

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Смена пароля текущего сотрудника")
data class ChangePasswordRequest(
    @field:Schema(description = "Действующий пароль", accessMode = Schema.AccessMode.WRITE_ONLY)
    val currentPassword: String,
    @field:Schema(
        description = "Новый пароль: 8–255 символов, строчная и заглавная буквы и цифра",
        minLength = 8,
        maxLength = 255,
        accessMode = Schema.AccessMode.WRITE_ONLY,
    )
    val newPassword: String,
)
