package ru.medovik.hack2026.api.service

import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import ru.medovik.hack2026.api.dto.AuthenticationResponse
import ru.medovik.hack2026.api.dto.CurrentUserResponse
import ru.medovik.hack2026.api.dto.LoginRequest
import ru.medovik.hack2026.api.exception.AuthenticationException
import ru.medovik.hack2026.api.exception.UserNotFoundException
import ru.medovik.hack2026.api.properties.AuthProperties
import ru.medovik.hack2026.api.repository.UserAccountRepository
import ru.medovik.hack2026.api.jooq.tables.pojos.UserAccounts
import java.time.Duration

@Service
class AuthenticationService(
    private val accounts: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val properties: AuthProperties,
) {
    fun login(request: LoginRequest, response: HttpServletResponse): AuthenticationResponse {
        val account = accounts.findByEmail(request.email.trim().lowercase())
            ?: throw AuthenticationException("Неверный email или пароль")
        if (!passwordEncoder.matches(request.password, account.passwordHash)) {
            throw AuthenticationException("Неверный email или пароль")
        }
        return createSession(account, response)
    }

    fun refresh(refreshToken: String, response: HttpServletResponse): AuthenticationResponse {
        val email = try {
            jwtService.subject(refreshToken)
        } catch (_: Exception) {
            throw AuthenticationException("Некорректный refresh-токен")
        }
        val account = accounts.findByEmail(email) ?: throw AuthenticationException("Некорректный refresh-токен")
        if (!jwtService.isValidRefreshToken(refreshToken, account.email)) {
            throw AuthenticationException("Некорректный refresh-токен")
        }
        return createSession(account, response)
    }

    fun currentUser(email: String): CurrentUserResponse = accounts.findByEmail(email)
        ?.toResponse()
        ?: throw UserNotFoundException("Учётная запись сотрудника не найдена")

    fun logout(response: HttpServletResponse) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
    }

    private fun createSession(account: UserAccounts, response: HttpServletResponse): AuthenticationResponse {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(jwtService.createRefreshToken(account)).toString())
        return AuthenticationResponse(
            accessToken = jwtService.createAccessToken(account),
            expiresIn = properties.accessTokenTtl.seconds,
            user = account.toResponse(),
        )
    }

    private fun refreshCookie(
        token: String,
        maxAge: Duration = properties.refreshTokenTtl,
    ): ResponseCookie = ResponseCookie.from("refresh_token", token)
        .httpOnly(true)
        .secure(properties.secureCookie)
        .sameSite("Strict")
        .path("/api/v1/auth")
        .maxAge(maxAge)
        .build()

    private fun UserAccounts.toResponse() = CurrentUserResponse(
        id = id,
        email = email,
        role = requireNotNull(role),
        createdAt = requireNotNull(createdAt),
    )
}
