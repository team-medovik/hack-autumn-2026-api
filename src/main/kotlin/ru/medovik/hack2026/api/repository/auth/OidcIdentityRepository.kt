package ru.medovik.hack2026.api.repository.auth

import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import ru.medovik.hack2026.api.jooq.tables.references.OIDC_IDENTITIES
import java.util.UUID

@Repository
class OidcIdentityRepository(
    private val dsl: DSLContext,
) {
    fun findByIssuerAndSubject(issuer: String, subject: String): OidcIdentity? = dsl
        .selectFrom(OIDC_IDENTITIES)
        .where(OIDC_IDENTITIES.ISSUER.eq(issuer))
        .and(OIDC_IDENTITIES.SUBJECT.eq(subject))
        .fetchOne { identity ->
            OidcIdentity(
                userAccountId = requireNotNull(identity.userAccountId),
                issuer = requireNotNull(identity.issuer),
                subject = requireNotNull(identity.subject),
            )
        }

    fun findByUserAccountId(accountId: UUID): OidcIdentity? = dsl
        .selectFrom(OIDC_IDENTITIES)
        .where(OIDC_IDENTITIES.USER_ACCOUNT_ID.eq(accountId))
        .fetchOne { identity ->
            OidcIdentity(
                userAccountId = requireNotNull(identity.userAccountId),
                issuer = requireNotNull(identity.issuer),
                subject = requireNotNull(identity.subject),
            )
        }

    fun insert(accountId: UUID, issuer: String, subject: String) {
        dsl.insertInto(OIDC_IDENTITIES)
            .set(OIDC_IDENTITIES.ID, UUID.randomUUID())
            .set(OIDC_IDENTITIES.USER_ACCOUNT_ID, accountId)
            .set(OIDC_IDENTITIES.ISSUER, issuer)
            .set(OIDC_IDENTITIES.SUBJECT, subject)
            .set(OIDC_IDENTITIES.CREATED_AT, DSL.currentOffsetDateTime())
            .execute()
    }
}
