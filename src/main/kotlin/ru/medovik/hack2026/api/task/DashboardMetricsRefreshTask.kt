package ru.medovik.hack2026.api.task

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.medovik.hack2026.api.service.dashboard.DashboardService

@Component
class DashboardMetricsRefreshTask(private val dashboard: DashboardService) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = $$"${app.dashboard.metrics-refresh-ms:30000}")
    fun refresh() {
        try {
            dashboard.getDashboard()
        } catch (exception: Exception) {
            logger.warn("Could not refresh dashboard metrics; retaining last successful values", exception)
        }
    }
}
