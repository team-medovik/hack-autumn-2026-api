package ru.medovik.hack2026.api.dto.settings

import io.swagger.v3.oas.annotations.media.Schema

import java.time.OffsetDateTime

@Schema(description = "Текущая версия настроек и распределение score по активному снимку")
data class ModelSettings(
    @field:Schema(description = "Текущая версия", minimum = "1", example = "1")
    val version: Long,
    @field:Schema(description = "Время последнего изменения с часовым поясом")
    val updatedAt: OffsetDateTime,
    @field:Schema(description = "Логин изменившего сотрудника", example = "admin")
    val updatedBy: String,
    @field:Schema(description = "Текущие значения")
    val values: ModelSettingsValues,
    @field:Schema(description = "Заводские значения")
    val defaults: ModelSettingsValues,
    @field:Schema(description = "101 корзина для score от 0 до 100; без снимка все нули")
    val scoreHistogram: List<Int>,
)
