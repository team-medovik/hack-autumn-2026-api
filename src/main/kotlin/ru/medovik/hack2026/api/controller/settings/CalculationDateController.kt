package ru.medovik.hack2026.api.controller.settings

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.settings.CalculationDate
import ru.medovik.hack2026.api.dto.response.common.ApiErrorResponse
import ru.medovik.hack2026.api.service.settings.CalculationDateService

@RestController
@RequestMapping("/api/v1/settings/calculation-date")
@Tag(name = "Настройки", description = "Параметры модели риска и дата расчёта")
@SecurityRequirement(name = "bearerAuth")
class CalculationDateController(
    private val service: CalculationDateService,
) {
    @GetMapping
    @Operation(
        operationId = "getCalculationDate",
        summary = "Получить дату расчёта",
        description = "Возвращает текущий режим и последний сохранённый момент UTC. " +
            "По умолчанию используется текущее время.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Дата расчёта",
            content = [Content(schema = Schema(implementation = CalculationDate::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "Требуется авторизация",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
    )
    fun getCalculationDate(): CalculationDate = service.get()

    @PutMapping
    @Operation(
        operationId = "saveCalculationDate",
        summary = "Сменить дату расчёта",
        description = "Режим fixed требует fixedAt. При current прежний fixedAt можно передать " +
            "для последующего возврата к фиксированному моменту. " +
            "Ответ приходит после проверки пересчёта активного снимка.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Сохранено и пересчитано",
            content = [Content(schema = Schema(implementation = CalculationDate::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "Требуется авторизация",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "422",
            description = "Некорректная дата расчёта",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
    )
    fun saveCalculationDate(@RequestBody request: CalculationDate): CalculationDate = service.save(request)
}
