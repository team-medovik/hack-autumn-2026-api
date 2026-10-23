package ru.medovik.hack2026.api.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.CookieValue
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.ApiErrorResponse
import ru.medovik.hack2026.api.dto.AuthenticationResponse
import ru.medovik.hack2026.api.dto.CurrentUserResponse
import ru.medovik.hack2026.api.dto.LoginRequest
import ru.medovik.hack2026.api.service.AuthenticationService

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Аутентификация", description = "Вход сотрудников и жизненный цикл JWT-сессии. Учётные записи заводит оператор; публичной регистрации и подтверждения email нет.")
class AuthenticationController(
    private val authenticationService: AuthenticationService,
) {
    @PostMapping("/login")
    @Operation(summary = "Войти в учётную запись сотрудника", description = "Проверяет учётные данные заранее заведённого сотрудника. Возвращает access-токен и устанавливает HttpOnly cookie refresh_token.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Сессия создана", content = [Content(schema = Schema(implementation = AuthenticationResponse::class))]),
        ApiResponse(responseCode = "400", description = "Некорректные учётные данные или запрос", content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]),
    )
    fun login(@RequestBody request: LoginRequest, response: HttpServletResponse): AuthenticationResponse =
        authenticationService.login(request, response)

    @PostMapping("/refresh")
    @Operation(summary = "Обновить сессию", description = "Ротирует cookie refresh-токена и возвращает новый access-токен. Refresh-токен не принимается в заголовке Authorization.")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Сессия обновлена", content = [Content(schema = Schema(implementation = AuthenticationResponse::class))]),
        ApiResponse(responseCode = "400", description = "Refresh-токен отсутствует или некорректен", content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]),
    )
    fun refresh(
        @Parameter(description = "HttpOnly cookie, устанавливаемая при входе.", required = true) @CookieValue("refresh_token") refreshToken: String,
        response: HttpServletResponse,
    ): AuthenticationResponse = authenticationService.refresh(refreshToken, response)

    @PostMapping("/logout")
    @Operation(summary = "Выйти из учётной записи", description = "Удаляет cookie refresh_token в браузере. Текущий access-токен остаётся действительным до окончания своего короткого срока жизни.")
    @ApiResponse(responseCode = "204", description = "Cookie refresh-токена удалена")
    fun logout(response: HttpServletResponse): ResponseEntity<Void> {
        authenticationService.logout(response)
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build()
    }

    @GetMapping("/me")
    @Operation(summary = "Получить текущего сотрудника", description = "Возвращает учётную запись, представленную корректным JWT access-токеном.", security = [SecurityRequirement(name = "bearerAuth")])
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Сотрудник получен", content = [Content(schema = Schema(implementation = CurrentUserResponse::class))]),
        ApiResponse(responseCode = "401", description = "Access-токен отсутствует, истёк или некорректен", content = [Content(schema = Schema(implementation = ApiErrorResponse::class))]),
    )
    fun me(@AuthenticationPrincipal email: String): CurrentUserResponse = authenticationService.currentUser(email)

    // TODO(OIDC): добавить /oidc/{provider}/login и callback после согласования discovery провайдера,
    // хранения state/nonce, разрешённого списка redirect URL и правил связывания учётных записей.
}
