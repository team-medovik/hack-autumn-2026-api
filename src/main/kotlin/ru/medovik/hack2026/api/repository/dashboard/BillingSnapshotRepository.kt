package ru.medovik.hack2026.api.repository.dashboard

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import ru.medovik.hack2026.api.jooq.tables.references.BILLING_SNAPSHOTS
import ru.medovik.hack2026.api.jooq.tables.records.BillingSnapshotsRecord
import java.time.DayOfWeek
import java.time.ZoneOffset
import java.time.temporal.TemporalAdjusters

@Repository
class BillingSnapshotRepository(
    private val dsl: DSLContext,
) {
    fun findLatest(): StoredBillingSnapshot? = dsl
        .selectFrom(BILLING_SNAPSHOTS)
        .orderBy(BILLING_SNAPSHOTS.TAKEN_AT.desc(), BILLING_SNAPSHOTS.ID.desc())
        .limit(1)
        .fetchOne()
        ?.toStored()

    fun findDashboardHistory(): List<StoredBillingSnapshot> {
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
