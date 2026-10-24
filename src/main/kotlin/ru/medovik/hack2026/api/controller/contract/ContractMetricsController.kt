package ru.medovik.hack2026.api.controller.contract

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.service.dashboard.DashboardService

/**
 * Business metrics in Prometheus 0.0.4 format; never put customer IDs or emails into labels.
 * Protect the port at the network/reverse-proxy layer; Prometheus scrapes internally.
 */
@RestController
@Tag(name = "Мониторинг")
class ContractMetricsController(private val dashboard: DashboardService) {
    @GetMapping("/metrics", produces = [MediaType.TEXT_PLAIN_VALUE])
    @Operation(operationId = "getRiskMetrics", summary = "Prometheus: клиенты по риску и ожидаемые потери")
    fun metrics(): ResponseEntity<String> {
        val summary = dashboard.getDashboard()
        val counts = summary.riskBuckets.associate { it.level to it.clients }
        val body = buildString {
            appendLine("# HELP churn_customers Number of customers by churn-risk level.")
            appendLine("# TYPE churn_customers gauge")
            for (level in listOf("high", "medium", "low", "none")) {
                append("churn_customers{level=\"").append(level).append("\"} ")
                appendLine(counts[level] ?: 0)
            }
            appendLine("# HELP churn_expected_monthly_loss_eur Estimated monthly loss in EUR.")
            appendLine("# TYPE churn_expected_monthly_loss_eur gauge")
            append("churn_expected_monthly_loss_eur ")
            appendLine(summary.monthlyLoss.toPlainString())
        }
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/plain; version=0.0.4; charset=utf-8"))
            .body(body)
    }
}
