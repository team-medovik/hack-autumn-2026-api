package ru.medovik.hack2026.api.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("app.auth")
data class AuthProperties(
    val jwtSecret: String,
    val accessTokenTtl: Duration = Duration.ofMinutes(15),
    val refreshTokenTtl: Duration = Duration.ofDays(30),
    val secureCookie: Boolean = true,
    val employees: List<EmployeeProperties> = emptyList(),
    val environmentEmployees: List<EmployeeProperties> = emptyList(),
) {
    data class EmployeeProperties(
        val email: String,
        val password: String,
        val role: AccountRole = AccountRole.EMPLOYEE,
    )

    enum class AccountRole {
        ADMIN,
        EMPLOYEE,
    }
}
