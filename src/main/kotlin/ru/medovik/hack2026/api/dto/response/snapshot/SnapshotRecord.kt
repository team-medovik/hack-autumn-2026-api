package ru.medovik.hack2026.api.dto.response.snapshot

import io.swagger.v3.oas.annotations.media.Schema
import java.time.OffsetDateTime

@Schema(description = "Запись истории загрузок снимков биллинга")
data class SnapshotRecord(
    @field:Schema(
        description = "Уникальный ID снимка для скачивания",
        example = "snapshot-550e8400-e29b-41d4-a716-446655440000",
    )
    val id: String,
    @field:Schema(description = "Имя исходного файла", example = "data.json")
    val fileName: String,
    @field:Schema(description = "Время загрузки с часовым поясом", example = "2026-10-23T22:10:00+03:00")
    val uploadedAt: OffsetDateTime,
    @field:Schema(description = "Логин загрузившего сотрудника", example = "analyst")
    val uploadedBy: String,
    @field:Schema(description = "Количество клиентов в снимке", minimum = "1", example = "42")
    val clients: Int,
    @field:Schema(description = "Количество услуг в снимке", minimum = "0", example = "118")
    val services: Int,
)
