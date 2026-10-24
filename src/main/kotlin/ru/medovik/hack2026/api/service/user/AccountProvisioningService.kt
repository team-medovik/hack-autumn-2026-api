package ru.medovik.hack2026.api.service.user

import org.slf4j.LoggerFactory
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.medovik.hack2026.api.dto.request.admin.CreateUserRequest
import ru.medovik.hack2026.api.enumerable.account.AccountSource
import ru.medovik.hack2026.api.jooq.tables.pojos.UserAccounts
import ru.medovik.hack2026.api.repository.auth.UserAccountRepository
import java.time.OffsetDateTime
import java.util.UUID

@Service
class AccountProvisioningService(
    private val repository: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val usersFile: ExternalUsersEnvFile,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun synchronizeExternalUsers() {
        usersFile.processIfChanged { definitions ->
            val emails = definitions.map { definition -> normalizedEmail(definition.email) }
            definitions.forEach(::upsertExternalUser)
            val deleted = repository.deleteExternalUsersExcept(emails)
            logger.info(
                "External users synchronized: configured={}, deleted={}",
                definitions.size,
                deleted,
            )
        }
    }

    fun createByAdmin(request: CreateUserRequest): UserAccounts {
        val email = normalizedEmail(request.email)
        require(repository.findByEmail(email) == null) { "Пользователь с email $email уже существует" }
        return repository.insert(
            UserAccounts(
                id = UUID.randomUUID(),
                email = email,
                passwordHash = requireNotNull(passwordEncoder.encode(request.password)),
                role = request.role.name,
                status = request.status.name,
                provisioningSource = AccountSource.ADMIN_PANEL.name,
                createdAt = OffsetDateTime.now(),
                updatedAt = OffsetDateTime.now(),
            )
        ).also { account ->
            logger.info("User account created from admin panel: email={}, role={}", account.email, account.role)
        }
    }

    fun listUsers(): List<UserAccounts> = repository.findAll()

    @Transactional
    fun deleteByAdmin(id: UUID) {
        val account = repository.findById(id)
            ?: throw NoSuchElementException("Учётная запись сотрудника не найдена")
        require(account.provisioningSource == AccountSource.ADMIN_PANEL.name) {
            "Пользователь ${account.email} управляется через окружение и не может быть удалён через API"
        }
        check(repository.deleteById(id) == 1) { "Не удалось удалить учётную запись пользователя" }
        logger.info("User account deleted from admin panel: id={}, email={}", id, account.email)
    }

    private fun upsertExternalUser(definition: ExternalUserDefinition) {
        val email = normalizedEmail(definition.email)
        val existing = repository.findByEmail(email)
        if (existing == null) {
            repository.insert(definition.toAccount(email))
            logger.info("User account created from env file: email={}, role={}", email, definition.role)
            return
        }

        require(existing.provisioningSource == AccountSource.ENV_FILE.name) {
            "Пользователь $email уже создан через панель"
        }
        repository.updateManagedAccount(
            id = requireNotNull(existing.id),
            passwordHash = requireNotNull(passwordEncoder.encode(definition.password)),
            role = definition.role.name,
            status = definition.status.name,
        )
        logger.info("User account updated from env file: email={}", email)
    }

    private fun normalizedEmail(value: String): String = value.trim().lowercase().also { email ->
        require(email.contains('@') && email.length <= 320) { "Некорректный email" }
    }

    private fun ExternalUserDefinition.toAccount(email: String) = UserAccounts(
        id = UUID.randomUUID(),
        email = email,
        passwordHash = requireNotNull(passwordEncoder.encode(password)),
        role = role.name,
        status = status.name,
        provisioningSource = AccountSource.ENV_FILE.name,
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now(),
    )
}
