package ru.medovik.hack2026.api.dto.settings

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Конфликт версий с актуальной версией настроек")
data class ModelSettingsConflict(
    @field:Schema(description = "Описание конфликта")
    val message: String,
    @field:Schema(description = "Текущая версия настроек для повторного редактирования")
    val current: ModelSettings,
)
