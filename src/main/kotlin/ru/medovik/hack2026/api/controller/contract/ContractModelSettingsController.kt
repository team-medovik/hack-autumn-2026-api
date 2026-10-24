package ru.medovik.hack2026.api.controller.contract

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.settings.ModelSettings
import ru.medovik.hack2026.api.dto.settings.PatchModelSettingsRequest
import ru.medovik.hack2026.api.service.settings.ModelSettingsService

@RestController
@Tag(name = "Модель")
@SecurityRequirement(name = "bearerAuth")
class ContractModelSettingsController(private val service: ModelSettingsService) {
    @GetMapping("/api/v1/model/settings")
    @Operation(operationId = "getRiskModelSettings", summary = "Получить действующие настройки модели")
    fun get(): ModelSettings = service.get()

    @PatchMapping("/api/v1/model/settings")
    @Operation(operationId = "patchRiskModelSettings", summary = "Частично обновить параметры модели и пересчитать")
    fun patch(
        @RequestBody request: PatchModelSettingsRequest,
        authentication: Authentication,
    ): ModelSettings = service.patch(request = request, updatedBy = authentication.name)
}
