package ru.medovik.hack2026.api.config.oidc

object OidcIntegrationPaths {
    const val BASE = "/api/v1/auth/oidc"
    const val LOGIN = "$BASE/{provider}/login"
    const val CALLBACK = "$BASE/{provider}/callback"
}
