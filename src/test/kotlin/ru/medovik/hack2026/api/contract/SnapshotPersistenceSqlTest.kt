package ru.medovik.hack2026.api.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jooq.DSLContext
import ru.medovik.hack2026.api.dto.response.snapshot.SnapshotRecord
import ru.medovik.hack2026.api.repository.dashboard.BillingSnapshotRepository
import java.lang.reflect.Proxy
import java.time.OffsetDateTime

/**
 * Tests the SQL and bind arguments sent by BillingSnapshotRepository without
 * relying on jOOQ's MockConnection update-count behavior. PostgreSQL requires
 * both timestamp parameters in this raw SQL insert to be cast to TIMESTAMPTZ.
 */
class SnapshotPersistenceSqlTest {
    @Test
    fun `snapshot insert casts both timestamps to prevent PostgreSQL varchar error`() {
        var capturedSql: String? = null
        var capturedArgs: List<Any?>? = null

        val dsl = Proxy.newProxyInstance(
            DSLContext::class.java.classLoader,
            arrayOf(DSLContext::class.java),
        ) { _, method, args ->
            if (method.name == "execute" && args?.getOrNull(0) is String) {
                capturedSql = args[0] as String
                val bindings = args.getOrNull(1) as? Array<*>
                    ?: error("Expected a vararg array of SQL parameters")
                capturedArgs = bindings.toList()
                1 // A successful INSERT affects exactly one row.
            } else {
                error("Unexpected DSLContext method: ${method.name}")
            }
        } as DSLContext

        val uploadedAt = OffsetDateTime.parse("2026-10-25T00:15:23Z")
        val record = SnapshotRecord(
            id = "snapshot-test",
            fileName = "data.json",
            uploadedAt = uploadedAt,
            uploadedBy = "tester",
            clients = 1,
            services = 1,
        )

        BillingSnapshotRepository(dsl).insert(
            record = record,
            payload = """{"accounts":[],"services":[],"transactions":[]}""",
            rawFile = "{}".toByteArray(Charsets.UTF_8),
        )

        val statement = requireNotNull(capturedSql) { "INSERT was not executed" }
        val cast = Regex("""cast\s*\(\s*\?\s+as\s+timestamptz\s*\)""", RegexOption.IGNORE_CASE)
        assertEquals(2, cast.findAll(statement).count(), statement)
        assertTrue(statement.contains("CAST(? AS jsonb)", ignoreCase = true), statement)

        val values = requireNotNull(capturedArgs)
        assertEquals(9, values.size, "Expected nine bound parameters")
        assertEquals(uploadedAt, values[1], "taken_at must use the uploaded timestamp")
        assertEquals(uploadedAt, values[2], "created_at must use the uploaded timestamp")
    }
}
