package ru.medovik.hack2026.api.config.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import ru.medovik.hack2026.api.dto.response.common.ApiErrorResponse
import tools.jackson.databind.json.JsonMapper

@Component
class ApiAuthenticationEntryPoint(
    private val jsonMapper: JsonMapper,
) : AuthenticationEntryPoint {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        logger.debug("Unauthenticated request rejected: method={}, path={}", request.method, request.requestURI)
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.characterEncoding = Charsets.UTF_8.name()
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        jsonMapper.writeValue(
            response.writer,
            ApiErrorResponse(
                status = HttpServletResponse.SC_UNAUTHORIZED,
                error = "UNAUTHORIZED",
                message = "Требуется аутентификация",
                path = request.requestURI,
            ),
        )
    }
}
