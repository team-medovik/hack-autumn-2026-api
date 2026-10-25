package ru.medovik.hack2026.api.dto.request.admin

import com.fasterxml.jackson.annotation.JsonAlias
import io.swagger.v3.oas.annotations.media.Schema
import ru.medovik.hack2026.api.enumerable.account.AccountRole
import ru.medovik.hack2026.api.enumerable.account.AccountStatus

@Schema(description = "Создание сотрудника. Временный пароль генерируется сервером и возвращается только в ответе 201.")
data class CreateUserRequest(
    @field:JsonAlias("email")
    @field:Schema(description = "Логин сотрудника, от 4 до 255 символов", example = "analyst")
    val login: String,
    @field:Schema(description = "Роль сотрудника", allowableValues = ["ADMIN", "EMPLOYEE"], example = "EMPLOYEE")
    val role: AccountRole = AccountRole.EMPLOYEE,
    @field:Schema(description = "Статус учётной записи", allowableValues = ["ACTIVE", "DISABLED"], example = "ACTIVE")
    val status: AccountStatus = AccountStatus.ACTIVE,
    @field:Schema(
        description = "Необязательный User ID пользователя в настроенном SSO-провайдере (OIDC sub). Это не Client ID приложения.",
        example = "0ba7a3fd-6fd8-48cd-a60b-2e8fd82d56e2",
    )
    val ssoUserId: String? = null,
)
