package ru.medovik.hack2026.api.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("app.ml")
data class MlProperties(
    val enabled: Boolean = false,
    val baseUrl: String = "http://ml-service:8000",
    val serviceToken: String = "",
    val modelVersion: String = "",
    val requestTimeout: Duration = Duration.ofSeconds(2),
    val cacheTtl: Duration = Duration.ofSeconds(30),
    val maxHistoryEvents: Int = 100_000,
) {
    init {
        require(!requestTimeout.isNegative && !requestTimeout.isZero && requestTimeout <= Duration.ofSeconds(30))
        require(!cacheTtl.isNegative && cacheTtl <= Duration.ofMinutes(5))
        require(maxHistoryEvents in 1..100_000)
        if (enabled) {
            val uri = URI(baseUrl)
            require(uri.scheme in setOf("http", "https") && uri.host != null && uri.userInfo == null &&
                uri.query == null && uri.fragment == null && uri.path in setOf("", "/"))
            require(serviceToken.length >= 16 && '\n' !in serviceToken && '\r' !in serviceToken)
            require(modelVersion.isNotBlank() && modelVersion.length <= 255) { "Pin APP_ML_MODEL_VERSION to the deployed model" }
        }
    }
}
