package ru.medovik.hack2026.api.dto.response.auth

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Аутентифицированный сотрудник и короткоживущий access-токен.")
data class AuthenticationResponse(
    @field:Schema(description = "JWT access-токен для заголовка Authorization: Bearer.")
    val accessToken: String,
    @field:Schema(description = "Время жизни access-токена в секундах.", example = "900")
    val expiresIn: Long,
    @field:Schema(description = "Данные вошедшего сотрудника")
    val user: CurrentUserResponse,
)
