package ru.medovik.hack2026.api.risk

import ru.medovik.hack2026.api.risk.model.CustomerState
import ru.medovik.hack2026.api.risk.model.PaymentTerm
import ru.medovik.hack2026.api.risk.model.RiskLevel
import ru.medovik.hack2026.api.risk.model.RiskResult
import ru.medovik.hack2026.api.risk.model.RiskSettings
import ru.medovik.hack2026.api.risk.model.RiskSnapshot
import ru.medovik.hack2026.api.risk.model.ServiceStatus
import ru.medovik.hack2026.api.risk.model.TransactionStatus
import ru.medovik.hack2026.api.risk.model.TransactionType
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.security.MessageDigest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FixtureRiskTest {
    private val mapper = jacksonObjectMapper()
    private val fixtureBytes = requireNotNull(javaClass.getResourceAsStream("/risk/data.json")) {
        "Original hackathon fixture is missing"
    }.use { it.readBytes() }
    private val snapshot = mapper.readValue<RiskSnapshot>(fixtureBytes)
    private val evaluationTime = Instant.parse("2026-10-19T15:00:00Z")
    private val settings = RiskSettings()

    private fun calculate(snapshot: RiskSnapshot = this.snapshot): RiskResult = RiskEngine().calculate(
        snapshot = snapshot,
        settings = settings,
        evaluationTime = evaluationTime,
    )

    @Test
    fun `original fixture is preserved and every entity deserializes`() {
        val digest = MessageDigest.getInstance("SHA-256").digest(fixtureBytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals("6e0e224671a4cd22059431b01fb6fed21e075100bb21b1545d94e746e666292e", digest)
        assertEquals(10, snapshot.accounts.size)
        assertEquals(30, snapshot.services.size)
        assertEquals(100, snapshot.transactions.size)
        assertEquals(10, snapshot.accounts.map { it.id }.toSet().size)
        assertEquals(30, snapshot.services.map { it.id }.toSet().size)
        assertEquals(100, snapshot.transactions.map { it.id }.toSet().size)
        assertEquals(PaymentTerm.entries.toSet(), snapshot.services.map { it.paymentTerm }.toSet())
        assertEquals(ServiceStatus.entries.toSet(), snapshot.services.map { it.status }.toSet())

        val accountIds = snapshot.accounts.map { it.id }.toSet()
        val serviceIds = snapshot.services.map { it.id }.toSet()
        snapshot.services.forEach {
            assertTrue(it.ownerId in accountIds)
            assertTrue(it.expiresAt.isAfter(it.createdAt))
        }
        assertEquals(30, snapshot.transactions.count { it.type == TransactionType.ORDER })
        assertEquals(70, snapshot.transactions.count { it.type == TransactionType.PROLONG })
        assertEquals(33, snapshot.transactions.count { it.status == TransactionStatus.NOT_PERFORMED })
        assertTrue(setOf(0, 1000).all { endpoint -> snapshot.transactions.any { it.amount == endpoint } })
        snapshot.transactions.forEach {
            assertTrue(it.serviceId in serviceIds)
            assertTrue(it.amount in 0..1000)
            assertEquals(it.status == TransactionStatus.PERFORMED, it.performedAt != null)
            assertTrue(it.performedAt == null || !it.performedAt.isBefore(it.createdAt))
        }
    }

    @Test
    fun `all thirty service scores match the documented formulas at fixture time`() {
        // A half-hour is 1/336 of the fixed seven-day horizon. No display rounding in the engine.
        val expectedScores = mapOf<Long, Double?>(
            1L to 50.0 * (1.0 - 1.0 / 336.0),
            2L to null,
            3L to null,
            4L to 15.0,
            5L to 70.0,
            6L to 0.0,
            7L to null,
            8L to null,
            9L to 65.0,
            10L to 70.0,
            11L to 0.0,
            12L to null,
            13L to null,
            14L to 15.0,
            15L to 70.0,
            16L to 0.0,
            17L to null,
            18L to null,
            19L to 15.0,
            20L to 70.0,
            21L to 0.0,
            22L to null,
            23L to null,
            24L to 15.0,
            25L to 70.0,
            26L to 50.0 / 336.0,
            27L to null,
            28L to null,
            29L to 15.0,
            30L to 70.0,
        )
        val result = calculate()
        assertEquals(evaluationTime, result.evaluationTime)
        assertEquals(settings, result.settings)
        assertEquals(expectedScores.keys, result.services.map { it.serviceId }.toSet())
        result.services.forEach { service ->
            val expected = expectedScores.getValue(service.serviceId)
            if (expected == null) {
                assertNull(service.score, "service ${service.serviceId}")
                assertNull(service.rawScore)
                assertNull(service.loyaltyScore)
                assertEquals(RiskLevel.NONE, service.level)
                assertTrue(service.features.isEmpty())
            } else {
                assertEquals(expected, assertNotNull(service.score), 1e-10, "service ${service.serviceId}")
                val expectedLevel = when {
                    expected >= 70 -> RiskLevel.HIGH
                    expected >= 40 -> RiskLevel.MEDIUM
                    else -> RiskLevel.LOW
                }
                assertEquals(expectedLevel, service.level, "service ${service.serviceId}")
                assertEquals(100.0 - expected, assertNotNull(service.loyaltyScore), 1e-10)
            }
        }
    }

    @Test
    fun `all ten customer scores and states match the source specification`() {
        val expected = mapOf(
            1L to Triple(50.0 * (1.0 - 1.0 / 336.0), RiskLevel.MEDIUM, CustomerState.EVALUATED),
            2L to Triple(null, RiskLevel.NONE, CustomerState.BLOCKED),
            3L to Triple(null, RiskLevel.NONE, CustomerState.ALL_SERVICES_DELETED),
            4L to Triple(15.0, RiskLevel.LOW, CustomerState.EVALUATED),
            5L to Triple(70.0, RiskLevel.HIGH, CustomerState.EVALUATED),
            6L to Triple(50.0 / 336.0, RiskLevel.LOW, CustomerState.EVALUATED),
            7L to Triple(null, RiskLevel.NONE, CustomerState.BLOCKED),
            8L to Triple(null, RiskLevel.NONE, CustomerState.ALL_SERVICES_DELETED),
            9L to Triple(65.0, RiskLevel.MEDIUM, CustomerState.EVALUATED),
            10L to Triple(70.0, RiskLevel.HIGH, CustomerState.EVALUATED),
        )
        val result = calculate()
        assertEquals(expected.keys, result.customers.map { it.accountId }.toSet())
        result.customers.forEach { customer ->
            val (score, level, state) = expected.getValue(customer.accountId)
            assertEquals(level, customer.level, "customer ${customer.accountId}")
            assertEquals(state, customer.state, "customer ${customer.accountId}")
            if (score == null) {
                assertNull(customer.attentionScore)
                assertNull(customer.loyaltyScore)
                assertEquals(0, customer.liveServices)
            } else {
                assertEquals(score, assertNotNull(customer.attentionScore), 1e-10, "customer ${customer.accountId}")
                assertEquals(100.0 - score, assertNotNull(customer.loyaltyScore), 1e-10)
                assertEquals(3, customer.liveServices)
            }
        }
        val clientFive = result.customers.single { it.accountId == 5L }
        assertEquals(28_657L, clientFive.funding.availableFunds)
        assertEquals(1_696L, clientFive.funding.requiredFunds)
        assertEquals(0L, clientFive.funding.shortfall)
        assertEquals(0.0, clientFive.funding.shortageStrength)
    }

    @Test
    fun `source specification client five zero-balance scenario reaches the cap`() {
        val result = calculate(snapshot.copy(accounts = snapshot.accounts.map { account ->
            if (account.id == 5L) account.copy(balance = 0) else account
        }))
        val customer = result.customers.single { it.accountId == 5L }
        assertEquals(0L, customer.funding.availableFunds)
        assertEquals(1_696L, customer.funding.requiredFunds)
        assertEquals(1_696L, customer.funding.shortfall)
        assertEquals(1.0, customer.funding.shortageStrength)
        assertEquals(100.0, customer.attentionScore)
        assertEquals(RiskLevel.HIGH, customer.level)
        assertEquals(0.0, customer.loyaltyScore)
        val affected = result.services.filter { it.ownerId == 5L }
        assertEquals(setOf(5L, 15L, 25L), affected.map { it.serviceId }.toSet())
        affected.forEach { service ->
            assertEquals(140.0, service.rawScore)
            assertEquals(100.0, service.score)
            assertEquals(
                mapOf("F2" to 40.0, "F3" to 70.0, "F4" to 30.0),
                service.features.filter { it.contribution > 0 }.associate { it.code to it.contribution },
            )
        }
    }

    @Test
    fun `fixture results expose reproducible contributions and sorted customer explanations`() {
        val result = calculate()
        result.services.filter { it.status.isLive }.forEach { service ->
            assertEquals(setOf("F1", "F2", "F3", "F4", "F5"), service.features.map { it.code }.toSet())
            service.features.forEach { feature ->
                assertTrue(feature.strength in 0.0..1.0)
                assertEquals(settings.weights.getValue(feature.code), feature.weight)
                assertEquals(feature.weight * feature.strength, feature.contribution, 1e-10)
                if (!feature.applicable) assertEquals(0.0, feature.strength)
                if (feature.contribution > 0) assertTrue(feature.explanation.isNotBlank())
            }
            val raw = service.features.sumOf { it.contribution }
            assertEquals(raw, assertNotNull(service.rawScore), 1e-10)
            assertEquals(raw.coerceAtMost(100.0), assertNotNull(service.score), 1e-10)
        }
        result.customers.forEach { customer ->
            val expectedExplanations = result.services.filter { it.ownerId == customer.accountId }
                .flatMap { service -> service.features.filter { it.contribution > 0 }.map { service.serviceId to it } }
                .toSet()
            val explanations = customer.explanations
            assertEquals(expectedExplanations, explanations.map { it.serviceId to it.feature }.toSet())
            assertTrue(explanations.all { it.feature.contribution > 0 })
            assertTrue(explanations.zipWithNext().all { (a, b) -> a.feature.contribution >= b.feature.contribution })
        }
    }

    @Test
    fun `transactions do not influence baseline risk and result survives JSON round trip`() {
        val result = calculate()
        assertEquals(result, calculate(snapshot.copy(transactions = emptyList())))
        assertEquals(result, mapper.readValue<RiskResult>(mapper.writeValueAsString(result)))
    }
}
