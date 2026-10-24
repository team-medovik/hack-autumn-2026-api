package ru.medovik.hack2026.api.dto.request.admin

import io.swagger.v3.oas.annotations.media.Schema
import ru.medovik.hack2026.api.enumerable.account.AccountRole
import ru.medovik.hack2026.api.enumerable.account.AccountStatus

@Schema(description = "Создание сотрудника. Временный пароль генерируется сервером и возвращается только в ответе 201.")
data class CreateUserRequest(
    val email: String,
    val role: AccountRole = AccountRole.EMPLOYEE,
    val status: AccountStatus = AccountStatus.ACTIVE,
)
