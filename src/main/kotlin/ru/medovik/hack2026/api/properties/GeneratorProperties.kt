package ru.medovik.hack2026.api.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("app.generator")
data class GeneratorProperties(
    val enabled: Boolean = false,
    val useForDashboard: Boolean = false,
    val sourceId: String = "aeza-live",
    val baseUrl: String = "http://generator-api:8000",
    val serviceToken: String = "",
    val pageSize: Int = 100,
    val applyBatchSize: Int = 1000,
    val requestTimeout: Duration = Duration.ofSeconds(10),
) {
    init {
        require(sourceId.matches(Regex("[A-Za-z0-9_-]{1,100}")))
        require(pageSize in 1..1000 && applyBatchSize in 1..1000)
        require(!requestTimeout.isNegative && !requestTimeout.isZero && requestTimeout <= Duration.ofSeconds(60))
        if (enabled) {
            val uri = URI(baseUrl)
            require(uri.scheme in setOf("http", "https") && uri.host != null && uri.userInfo == null &&
                uri.query == null && uri.fragment == null && uri.path in setOf("", "/"))
            require(serviceToken.length >= 16 && '\n' !in serviceToken && '\r' !in serviceToken)
        }
        require(!useForDashboard || enabled) { "Live dashboard requires enabled generator consumer" }
    }
}
