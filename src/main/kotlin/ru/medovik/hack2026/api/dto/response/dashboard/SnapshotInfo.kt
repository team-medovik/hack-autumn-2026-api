package ru.medovik.hack2026.api.dto.response.dashboard

import java.time.OffsetDateTime

data class SnapshotInfo(
    val id: String,
    val takenAt: OffsetDateTime,
)
