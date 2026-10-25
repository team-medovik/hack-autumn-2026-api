package ru.medovik.hack2026.api.service.auth

import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import ru.medovik.hack2026.api.dto.request.auth.LoginRequest
import ru.medovik.hack2026.api.dto.request.auth.ChangePasswordRequest
import ru.medovik.hack2026.api.dto.response.auth.AuthenticationResponse
import ru.medovik.hack2026.api.dto.response.auth.CurrentUserResponse
import ru.medovik.hack2026.api.exception.AuthenticationException
import ru.medovik.hack2026.api.exception.UserNotFoundException
import ru.medovik.hack2026.api.exception.InvalidCurrentPasswordException
import ru.medovik.hack2026.api.exception.WeakPasswordException
import ru.medovik.hack2026.api.jooq.tables.pojos.UserAccounts
import ru.medovik.hack2026.api.properties.AuthProperties
import ru.medovik.hack2026.api.repository.auth.UserAccountRepository
import ru.medovik.hack2026.api.service.user.AccountProvisioningService
import java.time.Duration

@Service
class AuthenticationService(
    private val accounts: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val properties: AuthProperties,
    private val provisioning: AccountProvisioningService,
) {
    private val logger = LoggerFactory.getLogger(AuthenticationService::class.java)

    fun login(
        request: LoginRequest,
        response: HttpServletResponse,
    ): AuthenticationResponse {
        provisioning.synchronizeExternalUsers()

        val account = accounts.findByLogin(request.login.trim().lowercase())
            ?: throw AuthenticationException("Неверный логин или пароль")
        if (account.status != "ACTIVE") {
            throw AuthenticationException("Учётная запись отключена")
        }
        if (!passwordEncoder.matches(request.password, account.passwordHash)) {
            throw AuthenticationException("Неверный логин или пароль")
        }

        logger.info("Password login succeeded: userId={}", account.id)
        return createSession(
            account = account,
            response = response,
        )
    }

    fun refresh(
        refreshToken: String,
        response: HttpServletResponse,
    ): AuthenticationResponse {
        val login = try {
            jwtService.subject(refreshToken)
        } catch (_: Exception) {
            throw AuthenticationException("Некорректный refresh-токен")
        }
        val account = accounts.findByLogin(login)
            ?: throw AuthenticationException("Некорректный refresh-токен")
        if (account.status != "ACTIVE") {
            throw AuthenticationException("Учётная запись отключена")
        }
        if (!jwtService.isValidRefreshToken(
                token = refreshToken,
                login = account.login,
                version = requireNotNull(account.refreshVersion),
            )
        ) {
            throw AuthenticationException("Некорректный refresh-токен")
        }

        return createSession(
            account = account,
            response = response,
        )
    }

    fun startOidcSession(login: String, response: HttpServletResponse) {
        val account = accounts.findActiveByLogin(login.trim().lowercase())
            ?: throw AuthenticationException("Учётная запись сотрудника не найдена или отключена")
        issueRefreshCookie(account, response)
    }

    fun currentUser(login: String): CurrentUserResponse = accounts.findByLogin(login.trim().lowercase())
        ?.toResponse()
        ?: throw UserNotFoundException("Учётная запись сотрудника не найдена")

    fun logout(request: HttpServletRequest, response: HttpServletResponse) {
        request.getSession(false)?.invalidate()
        SecurityContextHolder.clearContext()
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            refreshCookie(
                token = "",
                maxAge = Duration.ZERO,
            ).toString(),
        )
        logger.info("Refresh session cookie cleared")
    }

    @org.springframework.transaction.annotation.Transactional
    fun changePassword(login: String, request: ChangePasswordRequest, response: HttpServletResponse) {
        val account = accounts.findByLogin(login) ?: throw UserNotFoundException("Учётная запись сотрудника не найдена")
        if (!passwordEncoder.matches(request.currentPassword, account.passwordHash)) {
            throw InvalidCurrentPasswordException()
        }
        val password = request.newPassword
        if (password.length !in 8..255 || !password.any(Char::isLowerCase) ||
            !password.any(Char::isUpperCase) || !password.any(Char::isDigit)
        ) {
            throw WeakPasswordException()
        }
        val changed = accounts.changePassword(
            id = requireNotNull(account.id),
            passwordHash = requireNotNull(passwordEncoder.encode(password)),
        )
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            refreshCookie(token = jwtService.createRefreshToken(changed)).toString(),
        )
        logger.info("Account password changed: userId={}", account.id)
    }

    private fun createSession(
        account: UserAccounts,
        response: HttpServletResponse,
    ): AuthenticationResponse {
        issueRefreshCookie(account, response)
        return AuthenticationResponse(
            accessToken = jwtService.createAccessToken(account),
            expiresIn = properties.accessTokenTtl.seconds,
            user = account.toResponse(),
        )
    }

    private fun issueRefreshCookie(account: UserAccounts, response: HttpServletResponse) {
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            refreshCookie(token = jwtService.createRefreshToken(account)).toString(),
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
        login = login,
        role = requireNotNull(role),
        createdAt = requireNotNull(createdAt),
    )
}
