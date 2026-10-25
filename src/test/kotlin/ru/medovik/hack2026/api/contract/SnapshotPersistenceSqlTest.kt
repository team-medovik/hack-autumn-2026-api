package ru.medovik.hack2026.api.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.jooq.tools.jdbc.MockConnection
import org.jooq.tools.jdbc.MockDataProvider
import org.jooq.tools.jdbc.MockResult
import ru.medovik.hack2026.api.dto.response.snapshot.SnapshotRecord
import ru.medovik.hack2026.api.repository.dashboard.BillingSnapshotRepository
import ru.medovik.hack2026.api.jooq.tables.references.BILLING_SNAPSHOTS
import java.time.OffsetDateTime

/**
 * Reproduces the statement submitted for a snapshot insert without requiring a live
 * PostgreSQL instance. Both date parameters must be explicitly cast to TIMESTAMPTZ:
 * raw DSLContext.execute() otherwise binds OffsetDateTime as VARCHAR in this project.
 */
class SnapshotPersistenceSqlTest {
    @Test
    fun `snapshot insert casts both timestamps to prevent PostgreSQL varchar error`() {
        var sql: String? = null
        val result = DSL.using(SQLDialect.POSTGRES).newResult(BILLING_SNAPSHOTS.ID)
        val connection = MockConnection(MockDataProvider { context ->
            sql = context.sql()
            arrayOf(MockResult(1, result))
        })
        val dsl = DSL.using(connection, SQLDialect.POSTGRES)
        val record = SnapshotRecord(
            id = "snapshot-test",
            fileName = "data.json",
            uploadedAt = OffsetDateTime.parse("2026-10-25T00:15:23Z"),
            uploadedBy = "tester",
            clients = 1,
            services = 1,
        )

        BillingSnapshotRepository(dsl).insert(
            record = record,
            payload = """{"accounts":[],"services":[],"transactions":[]}""",
            rawFile = "{}".toByteArray(Charsets.UTF_8),
        )

        val statement = requireNotNull(sql) { "INSERT was not executed" }
        val cast = Regex("""cast\s*\(\s*\?\s+as\s+timestamptz\s*\)""", RegexOption.IGNORE_CASE)
        assertEquals(2, cast.findAll(statement).count(), statement)
        assertTrue(statement.contains("CAST(? AS jsonb)", ignoreCase = true), statement)
    }
}
