package ru.medovik.hack2026.api.config.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(100)
class RequestLoggingFilter : OncePerRequestFilter() {
    @Suppress("PROPERTY_HIDES_JAVA_FIELD")
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.requestURI.contains("/api/v1/docs") ||
        request.requestURI.contains("/swagger") ||
        request.requestURI.contains("/actuator")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val requestId = request.getHeader("X-Request-ID")
            ?.takeIf { it.length in 1..128 }
            ?: UUID.randomUUID().toString()
        val started = System.nanoTime()
        MDC.put("requestId", requestId)
        response.setHeader("X-Request-ID", requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            val elapsedMs = (System.nanoTime() - started) / 1_000_000
            logger.info(
                "HTTP request completed: method={}, path={}, status={}, durationMs={}",
                request.method,
                request.requestURI,
                response.status,
                elapsedMs,
            )
            MDC.remove("requestId")
        }
    }
}
