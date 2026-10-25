package ru.medovik.hack2026.api.service.generator

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import ru.medovik.hack2026.api.properties.GeneratorProperties
import java.time.Instant

@Component
class GeneratorConsumer(
    private val source: GeneratorSource,
    private val inbox: GeneratorInbox,
    private val properties: GeneratorProperties,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${app.generator.poll-delay-ms:1000}")
    fun poll() {
        if (!properties.enabled) return
        val id = properties.sourceId
        val initial = inbox.progress(id)
        if (initial != null && initial.retryAfter.toInstant() > Instant.now()) return
        // Drain durable work even if the generator is unavailable after a backend restart.
        try {
            inbox.applyNext(id, properties.applyBatchSize)
        } catch (_: Exception) {
            inbox.failed(id, "APPLY_FAILED")
            logger.warn("Generator consumer {}: application failed; cursor preserved", id)
            return
        }
        try {
            if (inbox.progress(id) == null) inbox.bind(id, source.runId())
            val cursor = requireNotNull(inbox.progress(id))
            // Do not grow an unbounded inbox behind an event that cannot be applied.
            if (cursor.receivedSequence - cursor.appliedSequence > properties.applyBatchSize * 2L) return
            val page = source.page(cursor.runId, cursor.receivedSequence)
            inbox.receive(id, cursor.receivedSequence, page)
        } catch (_: Exception) {
            inbox.failed(id, "FETCH_FAILED")
            logger.warn("Generator consumer {}: fetch failed; run binding and cursor preserved", id)
            return
        }
        try {
            inbox.applyNext(id, properties.applyBatchSize)
        } catch (_: Exception) {
            inbox.failed(id, "APPLY_FAILED")
            logger.warn("Generator consumer {}: application failed; cursor preserved", id)
        }
    }
}
