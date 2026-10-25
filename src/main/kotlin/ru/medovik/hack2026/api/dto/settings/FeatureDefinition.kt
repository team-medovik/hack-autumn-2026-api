package ru.medovik.hack2026.api.dto.settings

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Признак риска; порядок элементов определяет порядок показа")
data class FeatureDefinition(
    @field:Schema(pattern = "^[a-z][a-zA-Z0-9]*$")
    val key: String,
    val label: String,
    val description: String,
)
