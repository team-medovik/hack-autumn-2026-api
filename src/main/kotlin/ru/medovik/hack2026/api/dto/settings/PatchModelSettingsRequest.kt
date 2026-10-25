package ru.medovik.hack2026.api.dto.settings

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Атомарный частичный PATCH с проверкой ожидаемой версии")
data class PatchModelSettingsRequest(
    val version: Long,
    val changes: ModelSettingsChanges,
)
