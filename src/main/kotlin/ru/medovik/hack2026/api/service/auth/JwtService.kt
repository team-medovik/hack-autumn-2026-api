package ru.medovik.hack2026.api.service.auth

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import ru.medovik.hack2026.api.jooq.tables.pojos.UserAccounts
import ru.medovik.hack2026.api.properties.AuthProperties
import java.time.Instant
import java.util.Date

@Service
class JwtService(
    private val properties: AuthProperties,
) {
    private val signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.jwtSecret))

    fun createAccessToken(account: UserAccounts): String = createToken(
        account = account,
        type = "access",
        ttl = properties.accessTokenTtl,
    )

    fun createRefreshToken(account: UserAccounts): String = createToken(
        account = account,
        type = "refresh",
        ttl = properties.refreshTokenTtl,
    )

    fun isValidAccessToken(token: String, login: String): Boolean = isValid(
        token = token,
        login = login,
        type = "access",
    )

    fun isValidRefreshToken(token: String, login: String): Boolean = isValid(
        token = token,
        login = login,
        type = "refresh",
    )

    private fun createToken(
        account: UserAccounts,
        type: String,
        ttl: java.time.Duration,
    ): String = Jwts.builder()
        .subject(account.login)
        .claim("uid", account.id.toString())
        .claim("role", account.role)
        .claim("typ", type)
        .issuedAt(Date.from(Instant.now()))
        .expiration(Date.from(Instant.now().plus(ttl)))
        .signWith(signingKey)
        .compact()

    fun subject(token: String): String = claims(token).subject

    private fun isValid(
        token: String,
        login: String,
        type: String,
    ): Boolean = try {
        claims(token).let { it.subject == login && it["typ"] == type && it.expiration.after(Date()) }
    } catch (_: Exception) {
        false
    }

    private fun claims(token: String): Claims = Jwts.parser()
        .verifyWith(signingKey)
        .build()
        .parseSignedClaims(token)
        .payload
}
