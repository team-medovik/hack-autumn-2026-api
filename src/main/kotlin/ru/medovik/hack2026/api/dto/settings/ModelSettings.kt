package ru.medovik.hack2026.api.dto.settings

import java.time.OffsetDateTime

data class ModelSettings(
    val version: Long,
    val updatedAt: OffsetDateTime,
    val updatedBy: String,
    val values: ModelSettingsValues,
    val defaults: ModelSettingsValues,
    val scoreHistogram: List<Int>,
)
