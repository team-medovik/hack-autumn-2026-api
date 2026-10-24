package ru.medovik.hack2026.api.repository.auth

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import ru.medovik.hack2026.api.enumerable.account.AccountSource
import ru.medovik.hack2026.api.jooq.tables.pojos.UserAccounts
import ru.medovik.hack2026.api.jooq.tables.references.USER_ACCOUNTS
import java.util.UUID

@Repository
class UserAccountRepository(
    private val dsl: DSLContext,
) {
    fun findByLogin(login: String): UserAccounts? = dsl
        .selectFrom(USER_ACCOUNTS)
        .where(USER_ACCOUNTS.LOGIN.eq(login))
        .fetchOneInto(UserAccounts::class.java)

    fun findActiveByLogin(login: String): UserAccounts? = dsl
        .selectFrom(USER_ACCOUNTS)
        .where(USER_ACCOUNTS.LOGIN.eq(login))
        .and(USER_ACCOUNTS.STATUS.eq("ACTIVE"))
        .fetchOneInto(UserAccounts::class.java)

    fun findById(id: UUID): UserAccounts? = dsl
        .selectFrom(USER_ACCOUNTS)
        .where(USER_ACCOUNTS.ID.eq(id))
        .fetchOneInto(UserAccounts::class.java)

    fun findAll(): List<UserAccounts> = dsl
        .selectFrom(USER_ACCOUNTS)
        .orderBy(USER_ACCOUNTS.CREATED_AT, USER_ACCOUNTS.LOGIN)
        .fetchInto(UserAccounts::class.java)

    fun lockActiveAdminIds(): List<UUID> = dsl
        .select(USER_ACCOUNTS.ID)
        .from(USER_ACCOUNTS)
        .where(USER_ACCOUNTS.ROLE.eq("ADMIN"))
        .and(USER_ACCOUNTS.STATUS.eq("ACTIVE"))
        .orderBy(USER_ACCOUNTS.ID)
        .forUpdate()
        .fetch(USER_ACCOUNTS.ID)
        .map { requireNotNull(it) }

    fun insert(account: UserAccounts): UserAccounts = dsl
        .insertInto(USER_ACCOUNTS)
        .set(dsl.newRecord(USER_ACCOUNTS, account))
        .returning()
        .fetchOneInto(UserAccounts::class.java)
        ?: error("Не удалось создать учётную запись пользователя")

    fun updateManagedAccount(
        id: UUID,
        passwordHash: String,
        role: String,
        status: String,
    ) {
        dsl.update(USER_ACCOUNTS)
            .set(USER_ACCOUNTS.PASSWORD_HASH, passwordHash)
            .set(USER_ACCOUNTS.ROLE, role)
            .set(USER_ACCOUNTS.STATUS, status)
            .set(USER_ACCOUNTS.UPDATED_AT, org.jooq.impl.DSL.currentOffsetDateTime())
            .where(USER_ACCOUNTS.ID.eq(id))
            .execute()
    }

    fun deleteExternalUsersExcept(logins: Collection<String>): Int {
        val condition = if (logins.isEmpty()) {
            USER_ACCOUNTS.PROVISIONING_SOURCE.eq(AccountSource.ENV_FILE.name)
        } else {
            USER_ACCOUNTS.PROVISIONING_SOURCE.eq(AccountSource.ENV_FILE.name)
                .and(USER_ACCOUNTS.LOGIN.notIn(logins))
        }
        return dsl.deleteFrom(USER_ACCOUNTS)
            .where(condition)
            .execute()
    }

    fun deleteById(id: UUID): Int = dsl
        .deleteFrom(USER_ACCOUNTS)
        .where(USER_ACCOUNTS.ID.eq(id))
        .execute()
}
