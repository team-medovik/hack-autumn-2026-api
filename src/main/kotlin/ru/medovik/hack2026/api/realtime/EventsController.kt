package ru.medovik.hack2026.api.realtime

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

@RestController
@Tag(name = "События")
@SecurityRequirement(name = "bearerAuth")
class EventsController(private val broadcaster: SseBroadcaster) {
    @GetMapping("/api/v1/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    @Operation(operationId = "getEvents", summary = "Получать уведомления об изменениях")
    fun events(): ResponseEntity<SseEmitter> = ResponseEntity.ok()
        .header("X-Accel-Buffering", "no")
        .cacheControl(CacheControl.noStore())
        .body(broadcaster.subscribe())
}
