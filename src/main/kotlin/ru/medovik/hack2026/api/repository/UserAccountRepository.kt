package ru.medovik.hack2026.api.repository

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import ru.medovik.hack2026.api.jooq.tables.pojos.UserAccounts
import ru.medovik.hack2026.api.jooq.tables.references.USER_ACCOUNTS
import java.util.UUID

@Repository
class UserAccountRepository(
    private val dsl: DSLContext,
) {
    fun findByEmail(email: String): UserAccounts? = dsl.selectFrom(USER_ACCOUNTS)
        .where(USER_ACCOUNTS.EMAIL.eq(email))
        .fetchOneInto(UserAccounts::class.java)

    fun insert(account: UserAccounts): UserAccounts = dsl.insertInto(USER_ACCOUNTS)
        .set(dsl.newRecord(USER_ACCOUNTS, account))
        .returning()
        .fetchOneInto(UserAccounts::class.java)
        ?: error("Не удалось создать учётную запись пользователя")

    fun updatePassword(id: UUID, passwordHash: String) {
        dsl.update(USER_ACCOUNTS)
            .set(USER_ACCOUNTS.PASSWORD_HASH, passwordHash)
            .set(USER_ACCOUNTS.UPDATED_AT, org.jooq.impl.DSL.currentOffsetDateTime())
            .where(USER_ACCOUNTS.ID.eq(id))
            .execute()
    }
}
