package ru.medovik.hack2026.api.dto.request.auth

import com.fasterxml.jackson.annotation.JsonAlias
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Учётные данные заранее заведённого сотрудника.")
data class LoginRequest(
    @field:JsonAlias("email")
    @field:Schema(description = "Логин сотрудника, от 4 до 255 символов.", example = "admin")
    val login: String,
    @field:Schema(
        description = "Пароль сотрудника. Никогда не возвращается API.",
        example = "correct-horse-battery-staple"
    )
    val password: String,
)
