package ru.medovik.hack2026.api.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.auth")
data class AuthProperties(
    val jwtSecret: String,
    val accessTokenTtl: Duration = Duration.ofMinutes(15),
    val refreshTokenTtl: Duration = Duration.ofDays(30),
    val secureCookie: Boolean = true,
    val oidc: OidcProperties = OidcProperties(),
)
