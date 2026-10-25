package ru.medovik.hack2026.api.realtime

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.io.IOException
import java.time.Duration
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

@Component
class SseBroadcaster {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val emitters = CopyOnWriteArraySet<SseEmitter>()
    private val lastEventId = AtomicLong()

    fun subscribe(): SseEmitter {
        val emitter = SseEmitter(STREAM_TIMEOUT.toMillis())
        emitters += emitter
        emitter.onCompletion { emitters -= emitter }
        emitter.onTimeout {
            emitters -= emitter
            emitter.complete()
        }
        emitter.onError { emitters -= emitter }
        send(emitter, SseEmitter.event().comment("connected"))
        return emitter
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun onEntityChanged(event: EntityChangedEvent) {
        val id = lastEventId.incrementAndGet().toString()
        emitters.forEach { emitter ->
            send(
                emitter,
                SseEmitter.event()
                    .id(id)
                    .name(EVENT_NAME)
                    .data(event, MediaType.APPLICATION_JSON),
            )
        }
        logger.debug("SSE event {} sent to {} clients", event, emitters.size)
    }

    @Scheduled(fixedRate = 20_000)
    fun heartbeat() {
        emitters.forEach { send(it, SseEmitter.event().comment("ping")) }
    }

    private fun send(emitter: SseEmitter, event: SseEmitter.SseEventBuilder) {
        try {
            emitter.send(event)
        } catch (exception: IOException) {
            emitters -= emitter
            // The servlet container dispatches I/O failures and completes the async request.
            // Calling completeWithError here would dispatch the same failure again.
            logger.debug("SSE client disconnected", exception)
        } catch (exception: RuntimeException) {
            emitters -= emitter
            runCatching { emitter.completeWithError(exception) }
            logger.warn("SSE client stream closed after send failure", exception)
        }
    }

    private companion object {
        const val EVENT_NAME = "entity-changed"
        val STREAM_TIMEOUT: Duration = Duration.ofMinutes(15)
    }
}
