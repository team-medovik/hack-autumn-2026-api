package ru.medovik.hack2026.api.controller

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MissingRequestCookieException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.http.converter.HttpMessageNotReadableException
import ru.medovik.hack2026.api.dto.ApiErrorResponse
import ru.medovik.hack2026.api.exception.AuthenticationException
import ru.medovik.hack2026.api.exception.UserNotFoundException

@RestControllerAdvice
class AuthenticationExceptionHandler {
    @ExceptionHandler(
        AuthenticationException::class,
        MissingRequestCookieException::class,
        HttpMessageNotReadableException::class
    )
    fun badRequest(exception: Exception, request: HttpServletRequest) = error(
        HttpStatus.BAD_REQUEST,
        "AUTHENTICATION_ERROR",
        exception.message ?: "Некорректный запрос аутентификации",
        request
    )

    @ExceptionHandler(UserNotFoundException::class)
    fun notFound(exception: UserNotFoundException, request: HttpServletRequest) = error(
        HttpStatus.NOT_FOUND,
        "USER_NOT_FOUND",
        exception.message ?: "Учётная запись сотрудника не найдена",
        request
    )

    private fun error(
        status: HttpStatus,
        code: String,
        message: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(status).body(ApiErrorResponse(status.value(), code, message, request.requestURI))
}
