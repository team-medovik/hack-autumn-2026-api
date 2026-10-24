package ru.medovik.hack2026.api.config.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import ru.medovik.hack2026.api.dto.response.common.ApiErrorResponse
import ru.medovik.hack2026.api.repository.auth.UserAccountRepository
import ru.medovik.hack2026.api.service.auth.JwtService
import tools.jackson.databind.json.JsonMapper

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val accounts: UserAccountRepository,
    private val jsonMapper: JsonMapper,
) : OncePerRequestFilter() {
    @Suppress("PROPERTY_HIDES_JAVA_FIELD")
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader(AUTHORIZATION_HEADER)
        if (header == null) {
            filterChain.doFilter(request, response)
            return
        }
        if (!header.startsWith(BEARER_PREFIX)) {
            writeInvalidToken(
                response = response,
                path = request.requestURI,
            )
            return
        }

        val token = header.removePrefix(BEARER_PREFIX)
        val email = try {
            jwtService.subject(token)
        } catch (_: Exception) {
            writeInvalidToken(
                response = response,
                path = request.requestURI,
            )
            return
        }
        val account = accounts.findByEmail(email)
        if (account == null || account.status != "ACTIVE" || !jwtService.isValidAccessToken(token, account.email)) {
            writeInvalidToken(
                response = response,
                path = request.requestURI,
            )
            return
        }

        val authority = SimpleGrantedAuthority("ROLE_${account.role}")
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(account.email, null, listOf(authority))
        filterChain.doFilter(request, response)
    }

    private fun writeInvalidToken(response: HttpServletResponse, path: String) {
        logger.warn("JWT authentication rejected: path={}", path)
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        jsonMapper.writeValue(
            response.writer,
            ApiErrorResponse(
                status = 401,
                error = "INVALID_TOKEN",
                message = "Access-токен некорректен или истёк",
                path = path
            )
        )
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}
