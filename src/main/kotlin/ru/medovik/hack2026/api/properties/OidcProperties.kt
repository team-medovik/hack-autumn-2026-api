package ru.medovik.hack2026.api.properties

data class OidcProperties(
    val enabled: Boolean = false,
    val provider: String? = null,
    val autoLinkByEmail: Boolean = false,
    val successRedirectUri: String = "/",
    val registrations: Map<String, OidcRegistrationProperties> = emptyMap(),
)
