package ru.medovik.hack2026.api.service.ml

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ru.medovik.hack2026.api.dto.response.risk.RiskResult
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

internal data class MlAuditRecord(
    val snapshotId: String,
    val base: RiskResult,
    val final: RiskResult,
    val batch: MlPredictionBatch,
    val fallback: String?,
)

/** Best-effort observability must never make a read request wait for a second
 * connection while it already holds its snapshot transaction connection. */
@Component
class MlAuditDispatcher internal constructor(
    private val write: (MlAuditRecord) -> Unit,
    capacity: Int,
) : DisposableBean {
    @Autowired
    constructor(audit: MlAssessmentAudit) : this({ record ->
        // audit is the Spring transaction proxy; this worker thread has no caller
        // transaction, and the existing REQUIRES_NEW boundary owns its connection.
        audit.save(record.snapshotId, record.base, record.final, record.batch, record.fallback)
    }, 32)

    private val logger = LoggerFactory.getLogger(javaClass)
    private val dropped = AtomicLong()
    internal val droppedCount: Long get() = dropped.get()
    private val executor = ThreadPoolExecutor(
        1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(capacity),
        { task -> Thread(task, "ml-assessment-audit").apply { isDaemon = true } },
        ThreadPoolExecutor.AbortPolicy(),
    )

    fun enqueue(snapshotId: String, base: RiskResult, final: RiskResult, batch: MlPredictionBatch, fallback: String?): Boolean {
        val record = MlAuditRecord(snapshotId, base, final, batch, fallback)
        return try {
            executor.execute {
                try {
                    write(record)
                } catch (exception: Exception) {
                    logger.warn("ML audit write failed: {}", exception.javaClass.simpleName)
                }
            }
            true
        } catch (_: RejectedExecutionException) {
            val count = dropped.incrementAndGet()
            if (count == 1L || count % 100L == 0L) {
                logger.warn("ML audit queue full or closed; discarded audits={}", count)
            }
            false
        }
    }

    override fun destroy() {
        executor.shutdown()
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                val discarded = executor.shutdownNow().size
                logger.warn("ML audit shutdown timed out; discarded queued audits={}", discarded)
            }
        } catch (_: InterruptedException) {
            executor.shutdownNow()
            Thread.currentThread().interrupt()
        }
    }
}
