package ru.medovik.hack2026.api.service.user

import org.jooq.exception.DataAccessException
import org.slf4j.LoggerFactory
import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.medovik.hack2026.api.dto.request.admin.CreateUserRequest
import ru.medovik.hack2026.api.enumerable.account.AccountSource
import ru.medovik.hack2026.api.exception.UserAlreadyExistsException
import ru.medovik.hack2026.api.exception.UserDeletionForbiddenException
import ru.medovik.hack2026.api.jooq.tables.pojos.UserAccounts
import ru.medovik.hack2026.api.repository.auth.UserAccountRepository
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.Base64
import java.util.UUID

@Service
class AccountProvisioningService(
    private val repository: UserAccountRepository,
    private val passwordEncoder: PasswordEncoder,
    private val usersFile: ExternalUsersEnvFile,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

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

    fun createByAdmin(request: CreateUserRequest): CreatedUser {
        val email = normalizedEmail(request.email)
        if (repository.findByEmail(email) != null) throw UserAlreadyExistsException("Пользователь с email $email уже существует")
        val temporaryPassword = ByteArray(24).also(secureRandom::nextBytes)
            .let(Base64.getUrlEncoder().withoutPadding()::encodeToString)
        val account = try {
            repository.insert(
                UserAccounts(
                    id = UUID.randomUUID(),
                    email = email,
                    passwordHash = requireNotNull(passwordEncoder.encode(temporaryPassword)),
                    role = request.role.name,
                    status = request.status.name,
                    provisioningSource = AccountSource.ADMIN_PANEL.name,
                    createdAt = OffsetDateTime.now(),
                    updatedAt = OffsetDateTime.now(),
                )
            )
        } catch (_: DuplicateKeyException) {
            throw UserAlreadyExistsException("Пользователь с email $email уже существует")
        } catch (exception: DataAccessException) {
            if (exception.sqlState() == "23505") throw UserAlreadyExistsException("Пользователь с email $email уже существует")
            throw exception
        }
        logger.info("User account created from admin panel: email={}, role={}", account.email, account.role)
        return CreatedUser(account, temporaryPassword)
    }

    fun listUsers(): List<UserAccounts> = repository.findAll()

    @Transactional
    fun deleteByAdmin(id: UUID, actorEmail: String) {
        val account = repository.findById(id)
            ?: throw NoSuchElementException("Учётная запись сотрудника не найдена")
        if (account.email == actorEmail) throw UserDeletionForbiddenException(
            "SELF_DELETION_FORBIDDEN", "Нельзя удалить собственную учётную запись"
        )
        require(account.provisioningSource == AccountSource.ADMIN_PANEL.name) {
            "Пользователь ${account.email} управляется через окружение и не может быть удалён через API"
        }
        if (account.role == "ADMIN" && account.status == "ACTIVE") {
            val activeAdminIds = repository.lockActiveAdminIds()
            if (id !in activeAdminIds) throw NoSuchElementException("Учётная запись сотрудника не найдена")
            if (activeAdminIds.size <= 1) throw UserDeletionForbiddenException(
                "LAST_ACTIVE_ADMIN", "Нельзя удалить последнего активного администратора"
            )
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
