package ru.medovik.hack2026.api.controller.contract

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.service.dashboard.DashboardMetricsRecorder

/**
 * Те же бизнес-метрики, что и в Actuator, без системных метрик и персональных данных.
 */
@RestController
@Tag(name = "Мониторинг")
class ContractMetricsController(private val prometheus: PrometheusMeterRegistry) {
    @GetMapping("/metrics", produces = [MediaType.TEXT_PLAIN_VALUE])
    @Operation(operationId = "getRiskMetrics", summary = "Prometheus: только бизнес-метрики дашборда")
    fun metrics(): ResponseEntity<String> {
        val naming = prometheus.config().namingConvention()
        val names = prometheus.meters.asSequence()
            .map { it.id }
            .filter { it.name.startsWith(DashboardMetricsRecorder.METRIC_PREFIX) }
            .map { naming.name(it.name, it.type, it.baseUnit) }
            .toSet()
        val body = prometheus.scrape("text/plain; version=0.0.4", names)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/plain; version=0.0.4; charset=utf-8"))
            .body(body)
    }
}
