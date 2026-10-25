package ru.medovik.hack2026.api.controller.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.medovik.hack2026.api.properties.GeneratorProperties
import ru.medovik.hack2026.api.repository.dashboard.BillingSnapshotRepository
import ru.medovik.hack2026.api.realtime.EntityChangedEvent
import ru.medovik.hack2026.api.realtime.RealtimeEntity
import ru.medovik.hack2026.api.service.generator.ConsumerProgress
import ru.medovik.hack2026.api.service.generator.GeneratorInbox

data class GeneratorConsumerStatus(val enabled: Boolean, val usedForDashboard: Boolean, val progress: ConsumerProgress?)
data class DashboardSourceMode(
    @field:Schema(description = "Источник данных для дашборда и карточек", allowableValues = ["snapshot", "generator"])
    val mode: String,
)

@RestController
@Tag(name = "Источник данных")
@SecurityRequirement(name = "bearerAuth")
class GeneratorConsumerController(
    private val inbox: GeneratorInbox,
    private val settings: GeneratorProperties,
    private val snapshots: BillingSnapshotRepository,
    private val events: ApplicationEventPublisher,
) {
    @GetMapping("/api/v1/admin/generator/consumer")
    @Operation(summary = "Курсоры и состояние обработки событий генератора")
    fun status() = GeneratorConsumerStatus(settings.enabled, snapshots.usesGeneratorForDashboard(), inbox.progress(settings.sourceId))

    @GetMapping("/api/v1/admin/source/mode")
    @Operation(operationId = "getDashboardSourceMode", summary = "Получить источник данных дашборда (ADMIN)")
    fun mode() = DashboardSourceMode(if (snapshots.usesGeneratorForDashboard()) "generator" else "snapshot")

    @PutMapping("/api/v1/admin/source/mode")
    @Operation(
        operationId = "setDashboardSourceMode",
        summary = "Выбрать снимок или генератор для дашборда (ADMIN)",
        description = "Переключает источник без удаления данных. При рестарте восстанавливается режим из APP_GENERATOR_USE_FOR_DASHBOARD.",
    )
    fun selectMode(@RequestBody request: DashboardSourceMode): DashboardSourceMode {
        val useGenerator = when (request.mode) {
            "snapshot" -> false
            "generator" -> true
            else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "mode must be snapshot or generator")
        }
        if (useGenerator && !settings.enabled) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Generator consumer is disabled")
        }
        if (snapshots.selectDashboardSource(useGenerator)) {
            events.publishEvent(EntityChangedEvent(RealtimeEntity.SNAPSHOT))
        }
        return mode()
    }
}
