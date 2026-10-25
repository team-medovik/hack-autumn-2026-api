package ru.medovik.hack2026.api.service.user

import ru.medovik.hack2026.api.enumerable.account.AccountRole
import ru.medovik.hack2026.api.enumerable.account.AccountStatus

data class ExternalUserDefinition(
    val login: String,
    val password: String?,
    val role: AccountRole,
    val status: AccountStatus,
    val oidcIssuer: String? = null,
    val oidcSubject: String? = null,
)
