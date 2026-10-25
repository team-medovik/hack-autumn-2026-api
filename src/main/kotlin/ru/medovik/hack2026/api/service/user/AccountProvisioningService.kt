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
import ru.medovik.hack2026.api.repository.auth.OidcIdentityRepository
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
    private val identities: OidcIdentityRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    @Transactional
    fun synchronizeExternalUsers() {
        usersFile.processIfChanged { definitions ->
            val logins = definitions.map { definition -> AccountLogin.normalize(definition.login) }
            definitions.forEach(::upsertExternalUser)
            val deleted = repository.deleteExternalUsersExcept(logins)
            logger.info(
                "External users synchronized: configured={}, deleted={}",
                definitions.size,
                deleted,
            )
        }
    }

    fun createByAdmin(request: CreateUserRequest, actorLogin: String): CreatedUser {
        val login = AccountLogin.normalize(request.login)
        if (repository.findByLogin(login) != null) {
            throw UserAlreadyExistsException("Пользователь с логином $login уже существует")
        }
        val temporaryPassword = ByteArray(24).also(secureRandom::nextBytes)
            .let(Base64.getUrlEncoder().withoutPadding()::encodeToString)
        val account = try {
            repository.insert(
                UserAccounts(
                    id = UUID.randomUUID(),
                    login = login,
                    passwordHash = requireNotNull(passwordEncoder.encode(temporaryPassword)),
                    role = request.role.name,
                    status = request.status.name,
                    provisioningSource = AccountSource.ADMIN_PANEL.name,
                    refreshVersion = 0L,
                    passwordSelfChanged = false,
                    createdAt = OffsetDateTime.now(),
                    updatedAt = OffsetDateTime.now(),
                )
            )
        } catch (_: DuplicateKeyException) {
            throw UserAlreadyExistsException("Пользователь с логином $login уже существует")
        } catch (exception: DataAccessException) {
            if (exception.sqlState() == "23505") {
                throw UserAlreadyExistsException("Пользователь с логином $login уже существует")
            }
            throw exception
        }
        logger.info(
            "User account created from admin panel: userId={}, login={}, role={}, actor={}",
            account.id,
            account.login,
            account.role,
            actorLogin,
        )
        return CreatedUser(account, temporaryPassword)
    }

    fun listUsers(): List<UserAccounts> = repository.findAll()

    @Transactional
    fun deleteByAdmin(id: UUID, actorLogin: String) {
        val account = repository.findById(id)
            ?: throw NoSuchElementException("Учётная запись сотрудника не найдена")
        if (account.login == actorLogin) throw UserDeletionForbiddenException(
            "SELF_DELETION_FORBIDDEN", "Нельзя удалить собственную учётную запись"
        )
        require(account.provisioningSource == AccountSource.ADMIN_PANEL.name) {
            "Пользователь ${account.login} управляется через окружение и не может быть удалён через API"
        }
        if (account.role == "ADMIN" && account.status == "ACTIVE") {
            val activeAdminIds = repository.lockActiveAdminIds()
            if (id !in activeAdminIds) throw NoSuchElementException("Учётная запись сотрудника не найдена")
            if (activeAdminIds.size <= 1) throw UserDeletionForbiddenException(
                "LAST_ACTIVE_ADMIN", "Нельзя удалить последнего активного администратора"
            )
        }
        check(repository.deleteById(id) == 1) { "Не удалось удалить учётную запись пользователя" }
        logger.info("User account deleted from admin panel: id={}, login={}, actor={}", id, account.login, actorLogin)
    }

    private fun upsertExternalUser(definition: ExternalUserDefinition) {
        val login = AccountLogin.normalize(definition.login)
        val existing = repository.findByLogin(login)
        if (existing == null) {
            val account = repository.insert(definition.toAccount(login = login))
            synchronizeOidcIdentity(account, definition)
            logger.info("User account created from env file: login={}, role={}", login, definition.role)
            return
        }

        require(existing.provisioningSource == AccountSource.ENV_FILE.name) {
            "Пользователь $login уже создан через панель"
        }
        repository.updateManagedAccount(
            id = requireNotNull(existing.id),
            passwordHash = encodedPassword(definition.password),
            role = definition.role.name,
            status = definition.status.name,
        )
        synchronizeOidcIdentity(existing, definition)
        logger.info("User account updated from env file: login={}", login)
    }

    private fun synchronizeOidcIdentity(account: UserAccounts, definition: ExternalUserDefinition) {
        val issuer = definition.oidcIssuer ?: return
        val subject = requireNotNull(definition.oidcSubject)
        val accountId = requireNotNull(account.id)
        val identity = identities.findByIssuerAndSubject(issuer, subject)
        require(identity == null || identity.userAccountId == accountId) {
            "OIDC-идентичность уже связана с другим пользователем"
        }
        val current = identities.findByUserAccountId(accountId)
        when {
            current == null -> identities.insert(accountId, issuer, subject)
            current.issuer != issuer || current.subject != subject -> identities.update(accountId, issuer, subject)
        }
    }

    private fun ExternalUserDefinition.toAccount(login: String) = UserAccounts(
        id = UUID.randomUUID(),
        login = login,
        passwordHash = encodedPassword(password),
        role = role.name,
        status = status.name,
        provisioningSource = AccountSource.ENV_FILE.name,
        refreshVersion = 0L,
        passwordSelfChanged = false,
        createdAt = OffsetDateTime.now(),
        updatedAt = OffsetDateTime.now(),
    )

    private fun encodedPassword(password: String?): String {
        val secret = password ?: ByteArray(32).also(secureRandom::nextBytes)
            .let(Base64.getUrlEncoder().withoutPadding()::encodeToString)
        return requireNotNull(passwordEncoder.encode(secret))
    }
}
