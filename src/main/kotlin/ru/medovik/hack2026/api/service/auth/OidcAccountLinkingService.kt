package ru.medovik.hack2026.api.service.auth

import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.access.AccessDeniedException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.medovik.hack2026.api.jooq.tables.pojos.UserAccounts
import ru.medovik.hack2026.api.repository.auth.OidcIdentityRepository
import ru.medovik.hack2026.api.repository.auth.UserAccountRepository
import ru.medovik.hack2026.api.service.user.AccountProvisioningService

@Service
class OidcAccountLinkingService(
    private val accounts: UserAccountRepository,
    private val identities: OidcIdentityRepository,
    private val provisioning: AccountProvisioningService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun resolve(
        issuer: String,
        subject: String,
        email: String?,
        emailVerified: Boolean,
    ): UserAccounts {
        provisioning.synchronizeExternalUsers()
        identities.findByIssuerAndSubject(
            issuer = issuer,
            subject = subject,
        )?.let { identity ->
            return accounts.findById(identity.userAccountId).activeOrDenied()
        }
        if (email.isNullOrBlank() || !emailVerified) {
            throw AccessDeniedException("OIDC-провайдер не подтвердил email")
        }
        val account = accounts.findActiveByLogin(email.trim().lowercase())
            ?: throw AccessDeniedException("Учётная запись сотрудника не найдена или отключена")

        identities.findByUserAccountId(account.id)?.let {
            throw AccessDeniedException("Учётная запись уже связана с другой OIDC-идентичностью")
        }
        try {
            identities.insert(
                accountId = account.id,
                issuer = issuer,
                subject = subject,
            )
            logger.info("OIDC identity linked: userId={}, issuer={}", account.id, issuer)
            return account
        } catch (_: DataIntegrityViolationException) {
            val linked = identities.findByIssuerAndSubject(
                issuer = issuer,
                subject = subject,
            )
                ?: throw AccessDeniedException("Не удалось безопасно привязать OIDC-идентичность")
            if (linked.userAccountId != account.id) {
                throw AccessDeniedException("OIDC-идентичность уже привязана к другой учётной записи")
            }
            return accounts.findById(linked.userAccountId).activeOrDenied()
        }
    }

    private fun UserAccounts?.activeOrDenied(): UserAccounts = when {
        this == null -> throw AccessDeniedException("Связанная учётная запись не найдена")
        status != "ACTIVE" -> throw AccessDeniedException("Учётная запись отключена")
        else -> this
    }
}
