package ru.medovik.hack2026.api.risk

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.medovik.hack2026.api.risk.model.*
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class RiskReportTest {
    @TempDir
    lateinit var directory: Path

    private val mapper = jacksonObjectMapper()
    private val externalTime = Instant.parse("2026-10-27T00:00:00Z")

    @Test
    fun `default report calculates original fixture and writes complete JSON into new directories`() {
        val output = directory.resolve("nested/reports/fixture.json")
        val result = runRiskReport(arrayOf("--output", output.toString()))

        assertEquals(Instant.parse("2026-10-19T15:00:00Z"), result.evaluationTime)
        assertEquals(30, result.services.size)
        assertEquals(10, result.customers.size)
        assertEquals(50.0 * (1.0 - 1.0 / 336.0), assertNotNull(result.services.single { it.serviceId == 1L }.score), 1e-10)
        assertEquals(result, mapper.readValue<RiskResult>(Files.readString(output)))
    }

    @Test
    fun `external input and explicit time determine results instead of fixture defaults`() {
        val input = writeSnapshot()
        val output = directory.resolve("external-report.json")
        val originalInput = Files.readString(input)
        val result = runRiskReport(arrayOf(
            "--input", input.toString(),
            "--at", externalTime.toString(),
            "--output", output.toString(),
        ))

        assertEquals(externalTime, result.evaluationTime)
        assertEquals(listOf(44L), result.services.map { it.serviceId })
        assertEquals(listOf(22L), result.customers.map { it.accountId })
        assertEquals(25.0, result.services.single().score)
        assertEquals(25.0, result.customers.single().attentionScore)
        assertEquals(originalInput, Files.readString(input))
        assertEquals(result, mapper.readValue<RiskResult>(Files.readString(output)))
    }

    @Test
    fun `external settings configure horizon weights and thresholds in result and serialized report`() {
        val input = writeSnapshot()
        val settings = RiskSettings(
            horizonDays = 14,
            weights = RiskSettings().weights + ("F1" to 80.0),
            mediumThreshold = 10.0,
            highThreshold = 50.0,
        )
        val settingsFile = directory.resolve("settings.json")
        Files.writeString(settingsFile, mapper.writeValueAsString(settings))
        val output = directory.resolve("configured-report.json")

        val result = runRiskReport(arrayOf(
            "--output", output.toString(),
            "--settings", settingsFile.toString(),
            "--at", externalTime.toString(),
            "--input", input.toString(),
        ))

        // 3.5 days / 14-day horizon means strength .75; .75 * weight 80 = 60.
        assertEquals(settings, result.settings)
        assertEquals(60.0, result.services.single().score)
        assertEquals(RiskLevel.HIGH, result.services.single().level)
        assertEquals(result, mapper.readValue<RiskResult>(Files.readString(output)))
    }

    @Test
    fun `external input requires explicit evaluation time before writing output`() {
        val input = writeSnapshot()
        val output = directory.resolve("missing-time.json")

        assertFailsWith<IllegalArgumentException> {
            runRiskReport(arrayOf("--input", input.toString(), "--output", output.toString()))
        }
        assertFalse(Files.exists(output))
    }

    @Test
    fun `output option is required`() {
        assertFailsWith<IllegalArgumentException> { runRiskReport(emptyArray()) }
        assertFailsWith<IllegalArgumentException> { runRiskReport(arrayOf("--at", externalTime.toString())) }
    }

    @Test
    fun `unknown duplicate and missing option values fail without creating output`() {
        val invalidOptions = listOf(
            listOf("--unknown", "value"),
            listOf("--output", directory.resolve("other-report.json").toString()),
            listOf("--at", externalTime.toString(), "--at", externalTime.toString()),
            listOf("--input"),
            listOf("--settings"),
            listOf("--at"),
            listOf("--input", "--at", externalTime.toString()),
        )
        invalidOptions.forEachIndexed { index, invalid ->
            val output = directory.resolve("invalid-$index.json")
            assertFailsWith<IllegalArgumentException>("options: $invalid") {
                runRiskReport((listOf("--output", output.toString()) + invalid).toTypedArray())
            }
            assertFalse(Files.exists(output), "invalid options created $output")
        }
    }

    @Test
    fun `output cannot overwrite input using the same absolute path`() {
        val input = writeSnapshot()
        val original = Files.readString(input)

        assertFailsWith<IllegalArgumentException> {
            runRiskReport(arrayOf(
                "--input", input.toString(), "--at", externalTime.toString(), "--output", input.toString(),
            ))
        }
        assertEquals(original, Files.readString(input))
    }

    @Test
    fun `output cannot overwrite input through an equivalent relative normalized path`() {
        val input = writeSnapshot()
        val original = Files.readString(input)
        val relativeAlias = Path.of("").toAbsolutePath().relativize(input.toAbsolutePath())
            .parent.resolve(".").resolve(input.fileName)
        assertFalse(relativeAlias.isAbsolute)

        assertFailsWith<IllegalArgumentException> {
            runRiskReport(arrayOf(
                "--input", input.toString(), "--at", externalTime.toString(), "--output", relativeAlias.toString(),
            ))
        }
        assertEquals(original, Files.readString(input))
    }

    @Test
    fun `output cannot overwrite model settings`() {
        val settingsFile = directory.resolve("settings.json")
        val original = mapper.writeValueAsString(RiskSettings())
        Files.writeString(settingsFile, original)

        assertFailsWith<IllegalArgumentException> {
            runRiskReport(arrayOf("--settings", settingsFile.toString(), "--output", settingsFile.toString()))
        }
        assertEquals(original, Files.readString(settingsFile))
    }

    @Test
    fun `missing or malformed input fails before an existing report is overwritten`() {
        val malformed = directory.resolve("malformed.json")
        Files.writeString(malformed, "{ not valid snapshot JSON")
        listOf(directory.resolve("missing.json"), malformed).forEachIndexed { index, input ->
            val output = directory.resolve("preserved-$index.json")
            Files.writeString(output, "existing report")

            assertFails {
                runRiskReport(arrayOf(
                    "--input", input.toString(), "--at", externalTime.toString(), "--output", output.toString(),
                ))
            }
            assertEquals("existing report", Files.readString(output))
        }
    }

    @Test
    fun `invalid evaluation time fails before creating output`() {
        val input = writeSnapshot()
        val output = directory.resolve("bad-time.json")

        assertFails {
            runRiskReport(arrayOf(
                "--input", input.toString(), "--at", "2026-10-27 definitely not ISO", "--output", output.toString(),
            ))
        }
        assertFalse(Files.exists(output))
    }

    private fun writeSnapshot(): Path {
        val snapshot = RiskSnapshot(
            accounts = listOf(Account(
                id = 22,
                email = "external@example.test",
                balance = 1_000,
                withdrawBalance = 0,
                totalReplenished = 0,
                bonusBalance = 0,
                bonusState = BonusState.LOCKED,
            )),
            services = listOf(Service(
                id = 44,
                name = "External service",
                ownerId = 22,
                paymentTerm = PaymentTerm.MONTH,
                autoProlong = false,
                createdAt = Instant.parse("2026-10-01T00:00:00Z"),
                expiresAt = Instant.parse("2026-10-30T12:00:00Z"),
                status = ServiceStatus.ACTIVE,
                price = 100,
            )),
        )
        return directory.resolve("snapshot.json").also { Files.writeString(it, mapper.writeValueAsString(snapshot)) }
    }
}
