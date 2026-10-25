package ru.medovik.hack2026.api.controller.auth

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.response.common.ApiErrorResponse
import ru.medovik.hack2026.api.dto.response.auth.AuthenticationResponse
import ru.medovik.hack2026.api.dto.response.auth.CurrentUserResponse
import ru.medovik.hack2026.api.dto.request.auth.LoginRequest
import ru.medovik.hack2026.api.service.auth.AuthenticationService

@RestController
@RequestMapping("/api/v1/auth")
@Tag(
    name = "Аутентификация",
    description = "Вход сотрудников и жизненный цикл JWT-сессии. " +
        "Учётные записи заводит оператор; публичной регистрации и подтверждения email нет."
)
class AuthenticationController(
    private val authenticationService: AuthenticationService,
) {
    @PostMapping("/login")
    @Operation(
        operationId = "login",
        summary = "Войти в учётную запись сотрудника",
        description = "Проверяет учётные данные заранее заведённого сотрудника. " +
            "Возвращает access-токен и устанавливает HttpOnly cookie refresh_token."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Сессия создана",
            content = [Content(schema = Schema(implementation = AuthenticationResponse::class))]
        ),
        ApiResponse(
            responseCode = "400",
            description = "Некорректное тело запроса",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]
        ),
        ApiResponse(
            responseCode = "401",
            description = "Неверный логин или пароль либо учётная запись отключена",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
    )
    fun login(
        @RequestBody request: LoginRequest,
        response: HttpServletResponse,
    ): AuthenticationResponse = authenticationService.login(
        request = request,
        response = response,
    )

    @PostMapping("/refresh")
    @Operation(
        operationId = "refreshSession",
        summary = "Обновить сессию",
        description = "Ротирует cookie refresh-токена и возвращает новый access-токен. " +
            "Refresh-токен не принимается в заголовке Authorization."
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Сессия обновлена",
            content = [Content(schema = Schema(implementation = AuthenticationResponse::class))]
        ),
        ApiResponse(
            responseCode = "401",
            description = "Refresh-токен отсутствует или некорректен",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]
        ),
    )
    fun refresh(
        @Parameter(
            description = "HttpOnly cookie, устанавливаемая при входе.",
            required = true
        )
        @CookieValue("refresh_token") refreshToken: String,
        response: HttpServletResponse,
    ): AuthenticationResponse = authenticationService.refresh(
        refreshToken = refreshToken,
        response = response,
    )

    @PostMapping("/logout")
    @Operation(
        operationId = "logout",
        summary = "Выйти из учётной записи",
        description = "Удаляет cookie refresh_token в браузере. Текущий access-токен " +
            "остаётся действительным до окончания своего короткого срока жизни."
    )
    @ApiResponse(responseCode = "204", description = "Cookie refresh-токена удалена")
    fun logout(request: HttpServletRequest, response: HttpServletResponse): ResponseEntity<Void> {
        authenticationService.logout(request, response)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    @GetMapping("/me")
    @Operation(
        operationId = "getCurrentUser",
        summary = "Получить текущего сотрудника",
        description = "Возвращает учётную запись, представленную корректным JWT access-токеном.",
        security = [SecurityRequirement(name = "bearerAuth")]
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Сотрудник получен",
            content = [Content(schema = Schema(implementation = CurrentUserResponse::class))]
        ),
        ApiResponse(
            responseCode = "401",
            description = "Access-токен отсутствует, истёк или некорректен",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]
        ),
    )

    fun me(authentication: Authentication): CurrentUserResponse =
        authenticationService.currentUser(authentication.name)
}
