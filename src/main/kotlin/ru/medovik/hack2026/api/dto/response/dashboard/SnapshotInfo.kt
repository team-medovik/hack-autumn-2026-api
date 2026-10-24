package ru.medovik.hack2026.api.dto.response.dashboard

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "Снимок биллинга, по которому рассчитан ответ.")
data class SnapshotInfo(
    @field:Schema(description = "Идентификатор снимка.", example = "snapshot-2026-10-23")
    val id: String,
    @field:Schema(description = "Время формирования снимка в ISO 8601 с часовым поясом.", example = "2026-10-23T22:10:00+03:00")
    val takenAt: OffsetDateTime,
)
