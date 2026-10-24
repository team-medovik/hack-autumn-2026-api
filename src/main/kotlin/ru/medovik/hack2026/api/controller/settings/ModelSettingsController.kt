package ru.medovik.hack2026.api.controller.settings

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.settings.ModelSettings
import ru.medovik.hack2026.api.dto.settings.ModelSettingsConflict
import ru.medovik.hack2026.api.dto.settings.SaveModelSettingsRequest
import ru.medovik.hack2026.api.dto.response.common.ApiErrorResponse
import ru.medovik.hack2026.api.service.settings.ModelSettingsService

@RestController
@RequestMapping("/api/v1/settings/model")
@Tag(name = "Настройки", description = "Параметры модели риска и дата расчёта")
@SecurityRequirement(name = "bearerAuth")
class ModelSettingsController(
    private val service: ModelSettingsService,
) {
    @GetMapping
    @Operation(
        operationId = "getModelSettings",
        summary = "Получить параметры модели риска",
        description = "Возвращает текущую версию, значения, заводские настройки " +
            "и распределение score по активному снимку.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Параметры получены",
            content = [Content(schema = Schema(implementation = ModelSettings::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "Требуется авторизация",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
    )
    fun getModelSettings(): ModelSettings = service.get()

    @PutMapping
    @Operation(
        operationId = "saveModelSettings",
        summary = "Сохранить параметры модели риска",
        description = "Сравнивает version с текущей версией, проверяет диапазоны " +
            "и возвращает новую версию после пересчёта распределения score.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Сохранено и пересчитано",
            content = [Content(schema = Schema(implementation = ModelSettings::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "Требуется авторизация",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "409",
            description = "Версия настроек устарела",
            content = [Content(schema = Schema(implementation = ModelSettingsConflict::class))],
        ),
        ApiResponse(
            responseCode = "422",
            description = "Некорректные параметры модели",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
    )
    fun saveModelSettings(
        @RequestBody request: SaveModelSettingsRequest,
        authentication: Authentication,
    ): ModelSettings = service.save(request = request, updatedBy = authentication.name)
}
