package ru.medovik.hack2026.api.task

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.medovik.hack2026.api.service.user.AccountProvisioningService

@Component
class ExternalUsersSynchronizationTask(
    private val accountProvisioningService: AccountProvisioningService,
) {
    @Scheduled(fixedDelay = 5_000)
    fun synchronizeExternalUsers() {
        accountProvisioningService.synchronizeExternalUsers()
    }
}
