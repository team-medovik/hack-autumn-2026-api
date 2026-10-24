package ru.medovik.hack2026.api.repository.dashboard

import java.time.OffsetDateTime

data class StoredBillingSnapshot(
    val id: String,
    val takenAt: OffsetDateTime,
    val payload: String,
)
