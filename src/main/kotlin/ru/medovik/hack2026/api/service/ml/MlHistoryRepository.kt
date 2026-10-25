package ru.medovik.hack2026.api.service.ml

import org.jooq.DSLContext
import org.jooq.JSONB
import org.springframework.stereotype.Repository
import ru.medovik.hack2026.api.repository.dashboard.StoredBillingSnapshot
import tools.jackson.databind.json.JsonMapper
import java.time.OffsetDateTime

/** Reads an immutable, fully published prefix captured with the public snapshot. */
@Repository
class MlHistoryRepository(private val dsl: DSLContext, private val json: JsonMapper) {
    fun read(stored: StoredBillingSnapshot, limit: Int): MlHistory? {
        val run = stored.sourceRunId ?: return null
        val sequence = stored.sourceSequence ?: return null
        if (!stored.live || sequence < 1 || sequence > limit) return null
        val rows = dsl.fetch("""SELECT sequence, recorded_at, payload FROM generator_inbox
            WHERE run_id = ? AND sequence <= ? AND applied_at IS NOT NULL ORDER BY sequence""", run, sequence)
        if (rows.size.toLong() != sequence) return null
        val at = stored.takenAt.toInstant()
        val events = rows.mapIndexed { index, row ->
            check(row.get("sequence", Long::class.java) == index.toLong() + 1) { "Published history gap" }
            check(row.get("recorded_at", OffsetDateTime::class.java).toInstant() <= at) { "History exceeds snapshot" }
            @Suppress("UNCHECKED_CAST")
            val event = json.readValue(row.get("payload", JSONB::class.java).data(), Map::class.java) as Map<String, Any?>
            // Email is unnecessary for model features; service_created.name remains
            // required by the existing event contract. Null optional fields are
            // omitted; required nullable keys remain part of the event contract.
            event.filter { (key, value) -> key != "email" &&
                (value != null || key in setOf("serviceId", "causationId") ||
                    (key == "paidThrough" && event["type"] == "payment_failed")) }
        }
        return MlHistory(runId = run, asOf = at, syntheticOnly = events.all { it["source"] == "synthetic" }, events = events)
    }
}
