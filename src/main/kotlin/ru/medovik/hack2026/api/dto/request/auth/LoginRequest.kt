package ru.medovik.hack2026.api.dto.request.auth

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Учётные данные заранее заведённого сотрудника.")
data class LoginRequest(
    @field:Schema(description = "Email сотрудника.", example = "admin@example.com")
    val email: String,
    @field:Schema(
        description = "Пароль сотрудника. Никогда не возвращается API.",
        example = "correct-horse-battery-staple"
    )
    val password: String,
)
