package ru.medovik.hack2026.api.repository.auth

import java.util.UUID

data class OidcIdentity(
    val userAccountId: UUID,
    val issuer: String,
    val subject: String,
)
