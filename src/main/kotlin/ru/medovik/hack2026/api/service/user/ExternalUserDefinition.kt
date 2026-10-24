package ru.medovik.hack2026.api.service.user

import ru.medovik.hack2026.api.enumerable.account.AccountRole
import ru.medovik.hack2026.api.enumerable.account.AccountStatus

data class ExternalUserDefinition(
    val email: String,
    val password: String,
    val role: AccountRole,
    val status: AccountStatus,
)
