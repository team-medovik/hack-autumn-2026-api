package ru.medovik.hack2026.api.controller.advice

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingRequestCookieException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.async.AsyncRequestNotUsableException
import org.springframework.web.util.DisconnectedClientHelper
import ru.medovik.hack2026.api.dto.response.common.ApiErrorResponse
import ru.medovik.hack2026.api.exception.AuthenticationException
import ru.medovik.hack2026.api.exception.UserAlreadyExistsException
import ru.medovik.hack2026.api.exception.OidcIdentityAlreadyExistsException
import ru.medovik.hack2026.api.exception.UserDeletionForbiddenException
import ru.medovik.hack2026.api.exception.UserNotFoundException
import ru.medovik.hack2026.api.exception.ModelSettingsConflictException
import ru.medovik.hack2026.api.exception.InvalidModelSettingsException
import ru.medovik.hack2026.api.dto.settings.ModelSettingsConflict
import ru.medovik.hack2026.api.exception.InvalidCurrentPasswordException
import ru.medovik.hack2026.api.exception.WeakPasswordException
import ru.medovik.hack2026.api.exception.InvalidSnapshotException
import ru.medovik.hack2026.api.service.snapshot.SnapshotTooLargeException

@RestControllerAdvice
class AuthenticationExceptionHandler {
    private val logger = LoggerFactory.getLogger(AuthenticationExceptionHandler::class.java)

    @ExceptionHandler(InvalidSnapshotException::class)
    fun invalidSnapshot(exception: InvalidSnapshotException, request: HttpServletRequest) = error(
        status = HttpStatus.UNPROCESSABLE_CONTENT,
        code = "UNPROCESSABLE_CONTENT",
        message = exception.message ?: "Некорректный файл снимка",
        request = request,
    )

    @ExceptionHandler(SnapshotTooLargeException::class)
    fun snapshotTooLarge(exception: SnapshotTooLargeException, request: HttpServletRequest) = error(
        status = HttpStatus.CONTENT_TOO_LARGE,
        code = "CONTENT_TOO_LARGE",
        message = exception.message ?: "Файл слишком большой",
        request = request,
    )

    @ExceptionHandler(InvalidCurrentPasswordException::class)
    fun invalidCurrentPassword(exception: InvalidCurrentPasswordException, request: HttpServletRequest) = error(
        status = HttpStatus.BAD_REQUEST,
        code = "INVALID_CURRENT_PASSWORD",
        message = exception.message ?: "Неверный текущий пароль",
        request = request,
    )

    @ExceptionHandler(WeakPasswordException::class)
    fun weakPassword(exception: WeakPasswordException, request: HttpServletRequest) = error(
        status = HttpStatus.UNPROCESSABLE_CONTENT,
        code = "WEAK_PASSWORD",
        message = exception.message ?: "Пароль не соответствует требованиям",
        request = request,
    )

    @ExceptionHandler(
        AuthenticationException::class,
        MissingRequestCookieException::class,
    )
    fun authenticationFailed(
        exception: Exception,
        request: HttpServletRequest,
    ) = error(
        status = HttpStatus.UNAUTHORIZED,
        code = "AUTHENTICATION_ERROR",
        message = exception.message ?: "Некорректный запрос аутентификации",
        request = request,
    )

    @ExceptionHandler(ModelSettingsConflictException::class)
    fun modelSettingsConflict(exception: ModelSettingsConflictException): ResponseEntity<ModelSettingsConflict> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(
            ModelSettingsConflict(
                message = exception.message ?: "Настройки уже сохранили в другой версии",
                current = exception.current,
            )
        )

    @ExceptionHandler(InvalidModelSettingsException::class)
    fun invalidModelSettings(exception: InvalidModelSettingsException, request: HttpServletRequest) = error(
        status = HttpStatus.UNPROCESSABLE_CONTENT,
        code = "UNPROCESSABLE_CONTENT",
        message = exception.message ?: "Некорректные параметры модели",
        request = request,
    )

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadableBody(exception: HttpMessageNotReadableException, request: HttpServletRequest) = error(
        status = if (request.requestURI in setOf(
                "/api/v1/settings/model", "/api/v1/model/settings", "/api/v1/source/snapshot", "/api/v1/settings/calculation-date", "/api/v1/snapshots"
            )) {
            HttpStatus.UNPROCESSABLE_CONTENT
        } else HttpStatus.BAD_REQUEST,
        code = if (request.requestURI in setOf(
                "/api/v1/settings/model", "/api/v1/model/settings", "/api/v1/source/snapshot", "/api/v1/settings/calculation-date", "/api/v1/snapshots"
            )) "UNPROCESSABLE_CONTENT" else "INVALID_REQUEST",
        message = "Некорректное тело запроса",
        request = request,
    )

    @ExceptionHandler(UserNotFoundException::class)
    fun userNotFound(
        exception: UserNotFoundException,
        request: HttpServletRequest,
    ) = error(
        status = HttpStatus.NOT_FOUND,
        code = "USER_NOT_FOUND",
        message = exception.message ?: "Учётная запись сотрудника не найдена",
        request = request,
    )

    @ExceptionHandler(UserAlreadyExistsException::class)
    fun userAlreadyExists(exception: UserAlreadyExistsException, request: HttpServletRequest) = error(
        status = HttpStatus.CONFLICT,
        code = "USER_ALREADY_EXISTS",
        message = exception.message ?: "Пользователь уже существует",
        request = request,
    )

    @ExceptionHandler(OidcIdentityAlreadyExistsException::class)
    fun oidcIdentityAlreadyExists(exception: OidcIdentityAlreadyExistsException, request: HttpServletRequest) = error(
        status = HttpStatus.CONFLICT,
        code = "OIDC_IDENTITY_ALREADY_EXISTS",
        message = exception.message ?: "SSO-пользователь уже привязан",
        request = request,
    )

    @ExceptionHandler(UserDeletionForbiddenException::class)
    fun userDeletionForbidden(exception: UserDeletionForbiddenException, request: HttpServletRequest) = error(
        status = HttpStatus.CONFLICT,
        code = exception.code,
        message = exception.message ?: "Удаление пользователя запрещено",
        request = request,
    )

    @ExceptionHandler(IllegalArgumentException::class)
    fun invalidRequest(
        exception: IllegalArgumentException,
        request: HttpServletRequest,
    ) = error(
        status = if (request.requestURI == "/api/v1/settings/calculation-date") {
            HttpStatus.UNPROCESSABLE_CONTENT
        } else HttpStatus.BAD_REQUEST,
        code = if (request.requestURI == "/api/v1/settings/calculation-date") {
            "UNPROCESSABLE_CONTENT"
        } else "INVALID_REQUEST",
        message = exception.message ?: "Некорректный запрос",
        request = request,
    )

    @ExceptionHandler(NoSuchElementException::class)
    fun resourceNotFound(
        exception: NoSuchElementException,
        request: HttpServletRequest,
    ) = error(
        status = HttpStatus.NOT_FOUND,
        code = "NOT_FOUND",
        message = exception.message ?: "Ресурс не найден",
        request = request,
    )

    @ExceptionHandler(Exception::class)
    fun unexpected(
        exception: Exception,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse>? {
        if (exception is AsyncRequestNotUsableException ||
            DisconnectedClientHelper.isClientDisconnectedException(exception)
        ) {
            // The response is no longer writable, including an already opened SSE stream.
            logger.debug("API client disconnected: method={}, path={}", request.method, request.requestURI)
            return null
        }
        logger.error(
            "Unhandled API error: method={}, path={}",
            request.method,
            request.requestURI,
            exception,
        )
        return error(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_ERROR",
            message = "Внутренняя ошибка сервера",
            request = request,
        )
    }

    private fun error(
        status: HttpStatus,
        code: String,
        message: String,
        request: HttpServletRequest,
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(status).body(
            ApiErrorResponse(
                status = status.value(),
                error = code,
                message = message,
                path = request.requestURI,
            )
        )
}
