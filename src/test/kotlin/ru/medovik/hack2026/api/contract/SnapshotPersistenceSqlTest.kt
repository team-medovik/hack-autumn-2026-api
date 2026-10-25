package ru.medovik.hack2026.api.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.charset.StandardCharsets

/**
 * Regression guard for the PostgreSQL 42804 error observed in production.
 *
 * The repository uses raw SQL with DSLContext.execute(), so explicit TIMESTAMPTZ
 * casts must remain on both date placeholders. This static guard does not depend
 * on a database, JDBC driver, or jOOQ mocking implementation.
 */
class SnapshotPersistenceSqlTest {
    @Test
    fun `snapshot insert casts both timestamps to prevent PostgreSQL varchar error`() {
        // Read the production Kotlin source from the checkout; GitHub Actions checks
        // out the full source tree before invoking Gradle.
        val file = java.io.File(
            "src/main/kotlin/ru/medovik/hack2026/api/repository/dashboard/BillingSnapshotRepository.kt"
        )
        assertTrue(file.isFile, "BillingSnapshotRepository.kt is missing from the project checkout")
        val kotlinSource = file.readText(StandardCharsets.UTF_8)
        val insert = kotlinSource.substringAfter("fun insert(record: SnapshotRecord, payload: String, rawFile: ByteArray) {")
            .substringBefore("    fun findLatest()")
        val sql = insert.substringAfter("INSERT INTO billing_snapshots")
            .substringBefore("\"\"\"")
        val dateCast = Regex("""CAST\s*\(\s*\?\s+AS\s+timestamptz\s*\)""", RegexOption.IGNORE_CASE)
        assertEquals(2, dateCast.findAll(sql).count(), "Both taken_at and created_at require casts")
        assertTrue(sql.contains("CAST(? AS jsonb)", ignoreCase = true), "payload requires JSONB cast")
    }
}
