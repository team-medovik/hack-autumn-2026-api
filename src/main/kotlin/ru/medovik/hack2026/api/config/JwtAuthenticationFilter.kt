package ru.medovik.hack2026.api.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import ru.medovik.hack2026.api.dto.ApiErrorResponse
import ru.medovik.hack2026.api.repository.UserAccountRepository
import ru.medovik.hack2026.api.service.JwtService
import tools.jackson.databind.json.JsonMapper

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val accounts: UserAccountRepository,
    private val jsonMapper: JsonMapper,
) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, filterChain: FilterChain) {
        val header = request.getHeader(AUTHORIZATION_HEADER)
        if (header == null) {
            filterChain.doFilter(request, response)
            return
        }
        if (!header.startsWith(BEARER_PREFIX)) {
            writeInvalidToken(response, request.requestURI)
            return
        }

        val token = header.removePrefix(BEARER_PREFIX)
        val email = try {
            jwtService.subject(token)
        } catch (_: Exception) {
            writeInvalidToken(response, request.requestURI)
            return
        }
        val account = accounts.findByEmail(email)
        if (account == null || !jwtService.isValidAccessToken(token, account.email)) {
            writeInvalidToken(response, request.requestURI)
            return
        }

        val authority = SimpleGrantedAuthority("ROLE_${account.role}")
        SecurityContextHolder.getContext().authentication = UsernamePasswordAuthenticationToken(account.email, null, listOf(authority))
        filterChain.doFilter(request, response)
    }

    private fun writeInvalidToken(response: HttpServletResponse, path: String) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        jsonMapper.writeValue(response.writer, ApiErrorResponse(401, "INVALID_TOKEN", "Access-токен некорректен или истёк", path))
    }

    private companion object {
        const val AUTHORIZATION_HEADER = "Authorization"
        const val BEARER_PREFIX = "Bearer "
    }
}
