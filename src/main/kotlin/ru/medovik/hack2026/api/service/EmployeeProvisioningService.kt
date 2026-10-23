package ru.medovik.hack2026.api.service

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.password.PasswordEncoder
import ru.medovik.hack2026.api.properties.AuthProperties
import ru.medovik.hack2026.api.properties.AuthProperties.EmployeeProperties
import ru.medovik.hack2026.api.repository.UserAccountRepository
import ru.medovik.hack2026.api.jooq.tables.pojos.UserAccounts
import java.time.OffsetDateTime
import java.util.UUID

@Configuration
class EmployeeProvisioningService {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Bean
    fun provisionEmployees(
        properties: AuthProperties,
        repository: UserAccountRepository,
        passwordEncoder: PasswordEncoder,
    ) = ApplicationRunner {
        (properties.employees + properties.environmentEmployees)
            .groupBy { it.email.trim().lowercase() }
            .forEach { (email, definitions) ->
                require(definitions.size == 1) { "Сотрудник $email указан в конфигурации более одного раза" }
                if (repository.findByEmail(email) == null) {
                    repository.insert(definitions.single().toAccount(email, passwordEncoder))
                    logger.info("Создана учётная запись сотрудника {}", email)
                }
            }
    }

    private fun EmployeeProperties.toAccount(email: String, passwordEncoder: PasswordEncoder) = UserAccounts(
        id = UUID.randomUUID(),
        email = email,
        passwordHash = requireNotNull(passwordEncoder.encode(password)),
        role = role.name,
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now(),
    )
}
