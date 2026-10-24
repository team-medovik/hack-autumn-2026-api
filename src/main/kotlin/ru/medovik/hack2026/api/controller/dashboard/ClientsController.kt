package ru.medovik.hack2026.api.controller.dashboard

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.response.dashboard.ClientsResponse
import ru.medovik.hack2026.api.service.dashboard.DashboardService

@RestController
@RequestMapping("/api/v1/clients")
@Tag(name = "Клиенты")
@SecurityRequirement(name = "bearerAuth")
class ClientsController(
    private val dashboardService: DashboardService,
) {
    @GetMapping
    @Operation(summary = "Страница сводной таблицы клиентов", operationId = "listClients")
    fun listClients(
        @RequestParam(required = false) level: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
        @RequestParam(required = false) page: String?,
        @RequestParam(required = false) pageSize: String?,
    ): ClientsResponse = dashboardService.listClients(
        level = level,
        search = search,
        sort = sort,
        order = order,
        page = page,
        pageSize = pageSize,
    )
}
