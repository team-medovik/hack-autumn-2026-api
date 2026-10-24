package ru.medovik.hack2026.api.controller.auth

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.request.auth.ChangePasswordRequest
import ru.medovik.hack2026.api.dto.response.common.ApiErrorResponse
import ru.medovik.hack2026.api.service.auth.AuthenticationService

@RestController
@RequestMapping("/api/v1/account")
@Tag(name = "Аккаунт", description = "Настройки учётной записи вошедшего сотрудника")
@SecurityRequirement(name = "bearerAuth")
class AccountController(
    private val authenticationService: AuthenticationService,
) {
    @PostMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        operationId = "changePassword",
        summary = "Сменить свой пароль",
        description = "Проверяет текущий пароль. Новый пароль: 8–255 символов, " +
            "строчная и заглавная буквы и цифра. Остальные refresh-сессии отзываются; " +
            "текущей выдаётся новая cookie.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Пароль изменён"),
        ApiResponse(
            responseCode = "400",
            description = "Неверный текущий пароль",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "Требуется авторизация",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "422",
            description = "Новый пароль не соответствует требованиям",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
    )
    fun changePassword(
        @RequestBody request: ChangePasswordRequest,
        authentication: Authentication,
        response: HttpServletResponse,
    ) {
        authenticationService.changePassword(
            login = authentication.name,
            request = request,
            response = response,
        )
    }
}
