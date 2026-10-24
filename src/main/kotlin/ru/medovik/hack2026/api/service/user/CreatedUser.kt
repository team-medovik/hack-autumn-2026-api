package ru.medovik.hack2026.api.service.user

import ru.medovik.hack2026.api.jooq.tables.pojos.UserAccounts

data class CreatedUser(
    val account: UserAccounts,
    val temporaryPassword: String,
)
