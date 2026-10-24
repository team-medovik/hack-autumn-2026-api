package ru.medovik.hack2026.api.controller.contract

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.response.client.ClientCard
import ru.medovik.hack2026.api.dto.response.client.ClientPosition
import ru.medovik.hack2026.api.dto.response.dashboard.ClientsResponse
import ru.medovik.hack2026.api.service.dashboard.DashboardService

/**
 * Contract-first routes from the hackathon specification. Legacy /clients endpoints are
 * retained while consumers migrate; both share one implementation and the same data.
 */
@RestController
@Tag(name = "Клиенты")
@SecurityRequirement(name = "bearerAuth")
class ContractCustomersController(private val dashboard: DashboardService) {
    @GetMapping("/api/v1/customers")
    @Operation(operationId = "listCustomers", summary = "Рейтинг клиентов с риском оттока")
    fun listCustomers(
        @RequestParam(required = false) level: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
        @RequestParam(required = false) page: String?,
        @RequestParam(required = false) pageSize: String?,
    ): ClientsResponse = dashboard.listClients(
        level = level, search = search, sort = sort, order = order, page = page, pageSize = pageSize,
    )

    @GetMapping("/api/v1/customers/{id}")
    @Operation(operationId = "getCustomer", summary = "Риск, причины и денежная оценка клиента")
    fun getCustomer(@PathVariable id: String): ClientCard = dashboard.getClientCard(id)

    @GetMapping("/api/v1/customers/{id}/position")
    @Operation(operationId = "getCustomerPosition", summary = "Позиция клиента в рейтинге")
    fun getCustomerPosition(
        @PathVariable id: String,
        @RequestParam(required = false) level: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): ClientPosition = dashboard.getClientPosition(
        id = id, level = level, search = search, sort = sort, order = order,
    )
}
