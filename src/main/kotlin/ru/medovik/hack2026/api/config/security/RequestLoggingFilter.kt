package ru.medovik.hack2026.api.config.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.core.Ordered
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class RequestLoggingFilter : OncePerRequestFilter() {
    @Suppress("PROPERTY_HIDES_JAVA_FIELD")
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.startsWith("/api/v1/docs") ||
        request.requestURI.startsWith("/api/v1/swagger") ||
        request.requestURI.startsWith("/swagger") ||
        request.requestURI.startsWith("/actuator")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader("X-Request-ID")
            ?.takeIf { REQUEST_ID.matches(it) }
            ?: UUID.randomUUID().toString()
        val started = System.nanoTime()
        MDC.put("requestId", requestId)
        response.setHeader("X-Request-ID", requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            val message = "HTTP request completed: method={}, path={}, status={}, durationMs={}"
            when {
                response.status >= 500 -> logger.error(
                    message, request.method, request.requestURI, response.status, elapsedMs,
                )
                response.status >= 400 -> logger.warn(
                    message, request.method, request.requestURI, response.status, elapsedMs,
                )
                request.method in setOf("POST", "PUT", "PATCH", "DELETE") || elapsedMs >= SLOW_REQUEST_MS ->
                    logger.info(
                        message, request.method, request.requestURI, response.status, elapsedMs,
                    )
                else -> logger.debug(
                    message, request.method, request.requestURI, response.status, elapsedMs,
                )
            }
            MDC.remove("requestId")
        }
    }

    private companion object {
        val REQUEST_ID = Regex("[A-Za-z0-9._-]{1,128}")
        const val SLOW_REQUEST_MS = 1_000L
    }
}
