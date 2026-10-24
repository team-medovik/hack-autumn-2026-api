package ru.medovik.hack2026.api.controller.dashboard

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.response.dashboard.DashboardResponse
import ru.medovik.hack2026.api.dto.response.common.ApiErrorResponse
import ru.medovik.hack2026.api.service.dashboard.DashboardService

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(
    name = "Дашборд",
    description = "Сводка рисков оттока по последнему снимку биллинга для главного экрана."
)
@SecurityRequirement(name = "bearerAuth")
class DashboardController(
    private val dashboardService: DashboardService,
) {
    @GetMapping
    @Operation(
        summary = "Получить сводку главного экрана",
        description = """
            Возвращает все данные главного экрана одним запросом: ожидаемые потери, уровни риска,
            самого рискованного клиента, очередь внимания, причины потерь и график оттока.

            Фронтенд может обновлять сводку раз в 30 секунд. Отсутствие снимка не считается ошибкой:
            в ответе будет \`snapshot: null\`, нулевые показатели и пустые массивы. Пустой график
            означает, что для расчёта истории пока недостаточно снимков.
        """,
        operationId = "getDashboard",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Сводка построена, в том числе для пустого состояния без снимков",
            content = [Content(schema = Schema(implementation = DashboardResponse::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "Access-токен отсутствует, истёк или некорректен",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
    )
    fun getDashboard(): DashboardResponse = dashboardService.getDashboard()
}
