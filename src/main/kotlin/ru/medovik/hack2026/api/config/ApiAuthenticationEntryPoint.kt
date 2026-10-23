package ru.medovik.hack2026.api.config

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import ru.medovik.hack2026.api.dto.ApiErrorResponse
import tools.jackson.databind.json.JsonMapper

@Component
class ApiAuthenticationEntryPoint(
    private val jsonMapper: JsonMapper,
) : AuthenticationEntryPoint {
    override fun commence(request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        jsonMapper.writeValue(response.writer, ApiErrorResponse(401, "UNAUTHORIZED", "Требуется аутентификация", request.requestURI))
    }
}
