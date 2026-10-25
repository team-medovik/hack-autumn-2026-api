package ru.medovik.hack2026.api.controller.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.response.common.ApiErrorResponse
import ru.medovik.hack2026.api.service.generator.GeneratorHttpClient
import ru.medovik.hack2026.api.service.generator.SimulationControlException
import ru.medovik.hack2026.api.service.generator.SimulationSpeedRequest
import tools.jackson.databind.JsonNode

@RestController
@SecurityRequirement(name = "bearerAuth")
class SimulationController(private val generator: GeneratorHttpClient) {
    // SecurityConfig restricts both routes under /api/v1/admin/** to ROLE_ADMIN.
    @GetMapping("/api/v1/admin/simulation")
    @Operation(summary = "Состояние и скорость живой симуляции (ADMIN)")
    fun status() = generator.simulationStatus()

    @PatchMapping("/api/v1/admin/simulation/speed")
    @Operation(summary = "Изменить скорость со следующего шага (ADMIN)",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(required = true,
            content = [Content(schema = Schema(implementation = SimulationSpeedRequest::class))]))
    fun speed(@RequestBody body: JsonNode) = generator.setSimulationSpeed(SimulationSpeedRequest.parse(body))

    @ExceptionHandler(SimulationControlException::class)
    fun controlError(exception: SimulationControlException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(exception.status).body(ApiErrorResponse(
            status = exception.status.value(), error = exception.code,
            message = requireNotNull(exception.message), path = request.requestURI,
        ))
}
