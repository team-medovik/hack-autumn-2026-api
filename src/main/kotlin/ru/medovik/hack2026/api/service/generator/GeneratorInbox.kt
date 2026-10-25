package ru.medovik.hack2026.api.service.generator

import org.jooq.DSLContext
import org.jooq.JSONB
import org.jooq.Record
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import ru.medovik.hack2026.api.dto.request.risk.RiskSnapshot
import ru.medovik.hack2026.api.repository.dashboard.StoredBillingSnapshot
import tools.jackson.databind.json.JsonMapper
import java.time.OffsetDateTime

data class ConsumerProgress(
    val sourceId: String, val runId: String, val receivedSequence: Long, val appliedSequence: Long,
    val publishedAt: OffsetDateTime?, val publishedRevision: Long?, val failureCount: Int,
    val retryAfter: OffsetDateTime, val lastError: String?,
)

@Repository
class GeneratorInbox(private val dsl: DSLContext, private val json: JsonMapper) {
    private val projector = BillingEventProjector()

    fun progress(source: String): ConsumerProgress? = dsl.fetchOne(
        "SELECT * FROM generator_consumers WHERE source_id = ?", source)?.let {
        ConsumerProgress(source, it.get("run_id", String::class.java),
            it.long("received_sequence"), it.long("applied_sequence"),
            it.get("published_at", OffsetDateTime::class.java), it.get("published_revision", Long::class.java),
            it.get("failure_count", Int::class.java), it.get("retry_after", OffsetDateTime::class.java),
            it.get("last_error", String::class.java))
    }

    fun bind(source: String, runId: String) {
        require(runId.isNotBlank() && runId.length <= 255)
        dsl.transaction { config ->
            val tx = DSL.using(config)
            tx.execute("INSERT INTO generator_consumers(source_id, run_id) VALUES (?, ?) ON CONFLICT (source_id) DO NOTHING", source, runId)
            check(tx.fetchOne("SELECT run_id FROM generator_consumers WHERE source_id = ?", source)!!.get(0) == runId) {
                "Generator run changed; explicit new source binding required"
            }
        }
    }

    /** Network is outside this transaction. Duplicates must be identical, never silently replaced. */
    fun receive(source: String, requestedAfter: Long, page: GeneratorPage) {
        page.validate(requestedAfter)
        dsl.transaction { config ->
            val tx = DSL.using(config)
            val row = requireNotNull(tx.fetchOne("SELECT * FROM generator_consumers WHERE source_id = ? FOR UPDATE", source))
            check(row.get("run_id") == page.runId)
            var received = row.long("received_sequence")
            check(requestedAfter <= received) { "Consumer cursor gap" }
            var previousAt = tx.fetchOne("SELECT occurred_at FROM generator_inbox WHERE run_id = ? AND sequence = ?",
                page.runId, requestedAfter)?.get(0, OffsetDateTime::class.java)?.toInstant()
            page.events.forEach { item ->
                val event = item.event
                check(previousAt == null || event.occurredAt >= previousAt)
                previousAt = event.occurredAt
                val encoded = json.writeValueAsString(event)
                if (item.sequence <= received) {
                    val old = requireNotNull(tx.fetchOne("SELECT event_id, payload FROM generator_inbox WHERE run_id = ? AND sequence = ?",
                        page.runId, item.sequence))
                    check(old.get("event_id") == event.id && json.readTree(old.get("payload", JSONB::class.java).data()) == json.readTree(encoded)) {
                        "Conflicting event redelivery"
                    }
                } else {
                    check(item.sequence == received + 1) { "Event sequence gap" }
                    // The unique event ID rejects the same event at a different sequence.
                    tx.execute("""INSERT INTO generator_inbox(run_id, sequence, event_id, occurred_at, recorded_at, payload)
                        VALUES (?, ?, ?, CAST(? AS timestamptz), CAST(? AS timestamptz), CAST(? AS jsonb))""",
                        page.runId, item.sequence, event.id, event.occurredAt.toString(), event.recordedAt.toString(), encoded)
                    received = item.sequence
                }
            }
            val previousRevision = row.long("source_revision")
            if (page.revision >= previousRevision && page.nextSequence >= row.long("received_sequence")) {
                val previousAsOf = row.get("source_as_of", OffsetDateTime::class.java)?.toInstant()
                check(previousAsOf == null || page.asOf >= previousAsOf) { "Source clock moved backwards" }
                tx.execute("""UPDATE generator_consumers SET received_sequence = ?, source_revision = ?,
                    source_as_of = CAST(? AS timestamptz), tail_complete = ?, updated_at = CURRENT_TIMESTAMP,
                    failure_count = CASE WHEN last_error = 'FETCH_FAILED' THEN 0 ELSE failure_count END,
                    retry_after = CASE WHEN last_error = 'FETCH_FAILED' THEN CURRENT_TIMESTAMP ELSE retry_after END,
                    last_error = CASE WHEN last_error = 'FETCH_FAILED' THEN NULL ELSE last_error END
                    WHERE source_id = ?""", received, page.revision, page.asOf.toString(), !page.hasMore, source)
            } else {
                check(received == row.long("received_sequence")) { "New events from stale revision" }
            }
        }
    }

    /** Claim a whole ordered stream, not arbitrary events: a shared wallet requires ordering. */
    fun applyNext(source: String, limit: Int = 1000): Int = dsl.transactionResult { config ->
        require(limit in 1..1000)
        val tx = DSL.using(config)
        val row = tx.fetchOne("SELECT * FROM generator_consumers WHERE source_id = ? FOR UPDATE SKIP LOCKED", source)
            ?: return@transactionResult 0
        val run = row.get("run_id", String::class.java)
        val applied = row.long("applied_sequence")
        val pending = tx.fetch("SELECT sequence, payload FROM generator_inbox WHERE run_id = ? AND sequence > ? ORDER BY sequence LIMIT ?",
            run, applied, limit)
        pending.forEachIndexed { i, event -> check(event.long("sequence") == applied + i + 1) }
        val projection = json.readValue(row.get("projection", JSONB::class.java).data(), RiskSnapshot::class.java)
        val events = pending.map { json.readValue(it.get("payload", JSONB::class.java).data(), BillingEvent::class.java) }
        val nextProjection = projector.apply(projection, events)
        val next = applied + events.size
        val encoded = json.writeValueAsString(nextProjection)
        tx.execute("UPDATE generator_inbox SET applied_at = CURRENT_TIMESTAMP WHERE run_id = ? AND sequence > ? AND sequence <= ?",
            run, applied, next)
        tx.execute("""UPDATE generator_consumers SET projection = CAST(? AS jsonb), applied_sequence = ?, updated_at = CURRENT_TIMESTAMP
            WHERE source_id = ?""", encoded, next, source)
        if (events.isNotEmpty()) tx.execute("""UPDATE generator_consumers SET failure_count = 0,
            last_error = NULL, retry_after = CURRENT_TIMESTAMP WHERE source_id = ?""", source)
        // Never expose a partial page or clock beyond the events applied to that snapshot.
        if (row.get("tail_complete", Boolean::class.java) && next == row.long("received_sequence")) {
            tx.execute("""UPDATE generator_consumers SET published_projection = projection,
                published_at = source_as_of, published_revision = source_revision,
                published_sequence = applied_sequence WHERE source_id = ?""", source)
        }
        events.size
    }

    fun failed(source: String, code: String) {
        require(code in setOf("FETCH_FAILED", "APPLY_FAILED"))
        dsl.execute("""UPDATE generator_consumers SET failure_count = LEAST(failure_count + 1, 16),
            retry_after = CURRENT_TIMESTAMP + make_interval(secs => CAST(LEAST(60, power(2, LEAST(failure_count, 6))) AS double precision)),
            last_error = ?, updated_at = CURRENT_TIMESTAMP WHERE source_id = ?""", code, source)
    }

    fun published(source: String): StoredBillingSnapshot? = dsl.fetchOne(
        "SELECT run_id, published_at, published_revision, published_projection, published_sequence FROM generator_consumers WHERE source_id = ? AND published_projection IS NOT NULL", source)
        ?.let { StoredBillingSnapshot("generator:${it.get("run_id")}:${it.get("published_revision")}",
            it.get("published_at", OffsetDateTime::class.java), it.get("published_projection", JSONB::class.java).data(), live = true,
            sourceRunId = it.get("run_id", String::class.java), sourceSequence = it.get("published_sequence", Long::class.java)) }

    private fun Record.long(field: String): Long = get(field, Long::class.java)
}
