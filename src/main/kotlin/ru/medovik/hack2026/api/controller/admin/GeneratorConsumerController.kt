package ru.medovik.hack2026.api.controller.admin

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.properties.GeneratorProperties
import ru.medovik.hack2026.api.service.generator.ConsumerProgress
import ru.medovik.hack2026.api.service.generator.GeneratorInbox

data class GeneratorConsumerStatus(val enabled: Boolean, val usedForDashboard: Boolean, val progress: ConsumerProgress?)

@RestController
@SecurityRequirement(name = "bearerAuth")
class GeneratorConsumerController(private val inbox: GeneratorInbox, private val settings: GeneratorProperties) {
    @GetMapping("/api/v1/admin/generator/consumer")
    @Operation(summary = "Курсоры и состояние обработки событий генератора")
    fun status() = GeneratorConsumerStatus(settings.enabled, settings.useForDashboard, inbox.progress(settings.sourceId))
}
