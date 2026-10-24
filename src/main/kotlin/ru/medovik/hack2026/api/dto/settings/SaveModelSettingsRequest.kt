package ru.medovik.hack2026.api.dto.settings

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Сохранение модели с проверкой версии из последнего GET")
data class SaveModelSettingsRequest(
    @field:Schema(description = "Ожидаемая версия", minimum = "1", example = "1")
    val version: Long,
    @field:Schema(description = "Новые значения всех параметров")
    val values: ModelSettingsValues,
)
