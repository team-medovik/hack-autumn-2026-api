package ru.medovik.hack2026.api.controller.dashboard

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.response.dashboard.DashboardResponse
import ru.medovik.hack2026.api.service.dashboard.DashboardService

@RestController
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Дашборд")
@SecurityRequirement(name = "bearerAuth")
class DashboardController(
    private val dashboardService: DashboardService,
) {
    @GetMapping
    @Operation(summary = "Получить сводку главного экрана", operationId = "getDashboard")
    fun getDashboard(): DashboardResponse = dashboardService.getDashboard()
}
