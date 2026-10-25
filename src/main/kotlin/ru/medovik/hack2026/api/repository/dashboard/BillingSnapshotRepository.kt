package ru.medovik.hack2026.api.repository.dashboard

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import ru.medovik.hack2026.api.dto.response.snapshot.SnapshotRecord
import ru.medovik.hack2026.api.jooq.tables.references.BILLING_SNAPSHOTS
import ru.medovik.hack2026.api.jooq.tables.records.BillingSnapshotsRecord
import java.time.DayOfWeek
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters
import ru.medovik.hack2026.api.service.generator.GeneratorInbox
import ru.medovik.hack2026.api.properties.GeneratorProperties

@Repository
class BillingSnapshotRepository(
    private val dsl: DSLContext,
    private val generator: GeneratorInbox,
    private val generatorProperties: GeneratorProperties,
) {
    fun listRecords(): List<SnapshotRecord> = dsl.selectFrom(BILLING_SNAPSHOTS)
        .orderBy(
            BILLING_SNAPSHOTS.CREATED_AT.desc(),
            BILLING_SNAPSHOTS.TAKEN_AT.desc(),
            BILLING_SNAPSHOTS.ID.desc(),
        )
        .fetch()
        .map { record ->
            SnapshotRecord(
                id = requireNotNull(record.id),
                fileName = requireNotNull(record.fileName),
                uploadedAt = requireNotNull(record.createdAt),
                uploadedBy = requireNotNull(record.uploadedBy),
                clients = requireNotNull(record.clientsCount),
                services = requireNotNull(record.servicesCount),
            )
        }

    fun file(id: String): Pair<String, ByteArray>? = dsl.selectFrom(BILLING_SNAPSHOTS)
        .where(BILLING_SNAPSHOTS.ID.eq(id))
        .fetchOne()
        ?.let { record ->
            requireNotNull(record.fileName) to (record.rawFile ?: requireNotNull(record.payload).data().toByteArray())
        }

    fun insert(record: SnapshotRecord, payload: String, rawFile: ByteArray) {
        // Plain SQL DSLContext.execute() has no generated-column type metadata: jOOQ
        // may bind OffsetDateTime as VARCHAR. Cast explicitly for PostgreSQL TIMESTAMPTZ.
        check(dsl.execute(
            """INSERT INTO billing_snapshots
                (id, taken_at, created_at, payload, file_name, uploaded_by, clients_count, services_count, raw_file)
                VALUES (?, CAST(? AS timestamptz), CAST(? AS timestamptz), CAST(? AS jsonb), ?, ?, ?, ?, ?)""",
            record.id,
            record.uploadedAt,
            record.uploadedAt,
            payload,
            record.fileName,
            record.uploadedBy,
            record.clients,
            record.services,
            rawFile,
        ) == 1) { "Не удалось сохранить снимок" }
    }
    fun findLatest(): StoredBillingSnapshot? = if (generatorProperties.useForDashboard) {
        generator.published(generatorProperties.sourceId)
    } else dsl
        .selectFrom(BILLING_SNAPSHOTS)
        .orderBy(
            BILLING_SNAPSHOTS.CREATED_AT.desc(),
            BILLING_SNAPSHOTS.TAKEN_AT.desc(),
            BILLING_SNAPSHOTS.ID.desc(),
        )
        .limit(1)
        .fetchOne()
        ?.toStored()

    fun findDashboardHistory(): List<StoredBillingSnapshot> {
        if (generatorProperties.useForDashboard) return listOfNotNull(findLatest())
        val latest = findLatest() ?: return emptyList()
        val firstWeek = latest.takenAt.toInstant().atZone(ZoneOffset.UTC).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .minusWeeks(8)
        val cutoff = firstWeek.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime()
        val recent = dsl.selectFrom(BILLING_SNAPSHOTS)
            .where(BILLING_SNAPSHOTS.TAKEN_AT.ge(cutoff))
            .orderBy(BILLING_SNAPSHOTS.TAKEN_AT.desc(), BILLING_SNAPSHOTS.ID.desc())
            .fetch()
            .map { it.toStored() }
        val boundary = dsl.selectFrom(BILLING_SNAPSHOTS)
            .where(BILLING_SNAPSHOTS.TAKEN_AT.lt(cutoff))
            .orderBy(BILLING_SNAPSHOTS.TAKEN_AT.desc(), BILLING_SNAPSHOTS.ID.desc())
            .limit(1)
            .fetchOne()
            ?.toStored()
        return recent + listOfNotNull(boundary)
    }

    private fun BillingSnapshotsRecord.toStored() = StoredBillingSnapshot(
        id = requireNotNull(id),
        takenAt = requireNotNull(takenAt),
        payload = requireNotNull(payload).data(),
    )
}
