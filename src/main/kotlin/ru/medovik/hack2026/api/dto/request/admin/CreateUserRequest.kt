package ru.medovik.hack2026.api.dto.request.admin

import ru.medovik.hack2026.api.enumerable.account.AccountRole
import ru.medovik.hack2026.api.enumerable.account.AccountStatus

data class CreateUserRequest(
    val email: String,
    val password: String,
    val role: AccountRole = AccountRole.EMPLOYEE,
    val status: AccountStatus = AccountStatus.ACTIVE,
)
