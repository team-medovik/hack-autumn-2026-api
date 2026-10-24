package ru.medovik.hack2026.api.config.user

import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ru.medovik.hack2026.api.service.user.AccountProvisioningService

@Configuration
class AccountProvisioningConfiguration {

    @Bean
    fun synchronizeExternalUsers(service: AccountProvisioningService) = ApplicationRunner {
        service.synchronizeExternalUsers()
    }
}
