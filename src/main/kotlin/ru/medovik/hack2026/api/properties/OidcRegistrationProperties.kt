package ru.medovik.hack2026.api.properties

data class OidcRegistrationProperties(
    val issuerUri: String,
    val clientId: String,
    val clientSecret: String? = null,
    val scopes: Set<String> = setOf("openid", "email", "profile"),
)
