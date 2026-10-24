package ru.medovik.hack2026.api.dto.settings

data class SaveModelSettingsRequest(
    val version: Long,
    val values: ModelSettingsValues,
)
