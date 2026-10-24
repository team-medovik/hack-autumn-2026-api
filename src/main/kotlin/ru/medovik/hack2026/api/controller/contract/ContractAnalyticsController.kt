package ru.medovik.hack2026.api.controller.contract

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.response.dashboard.DashboardResponse
import ru.medovik.hack2026.api.service.dashboard.DashboardService

@RestController
@Tag(name = "Аналитика")
@SecurityRequirement(name = "bearerAuth")
class ContractAnalyticsController(private val dashboard: DashboardService) {
    @GetMapping("/api/v1/analytics/summary")
    @Operation(operationId = "getAnalyticsSummary", summary = "Сводка риска и ожидаемых месячных потерь")
    fun summary(): DashboardResponse = dashboard.getDashboard()
}
