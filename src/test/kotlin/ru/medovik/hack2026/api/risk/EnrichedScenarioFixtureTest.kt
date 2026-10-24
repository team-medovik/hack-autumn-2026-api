package ru.medovik.hack2026.api.risk

import ru.medovik.hack2026.api.dto.request.risk.PaymentTerm
import ru.medovik.hack2026.api.dto.request.risk.RiskSnapshot
import ru.medovik.hack2026.api.dto.request.risk.ServiceStatus
import ru.medovik.hack2026.api.service.risk.RiskEngine
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EnrichedScenarioFixtureTest {
    private val evaluationTime = Instant.parse("2026-10-19T15:00:00Z")
    private val snapshot: RiskSnapshot = requireNotNull(
        javaClass.getResourceAsStream("/risk/enriched-review-scenarios.json")
    ).use { jacksonObjectMapper().readValue<RiskSnapshot>(it.readBytes()) }

    @Test
    fun `enriched snapshot deserializes and preserves references through calculation`() {
        assertEquals(28, snapshot.accounts.size)
        assertEquals(45, snapshot.services.size)
        assertEquals(6, snapshot.transactions.size)
        val accountIds = snapshot.accounts.map { it.id }.toSet()
        val serviceIds = snapshot.services.map { it.id }.toSet()
        assertEquals(snapshot.accounts.size, accountIds.size)
        assertEquals(snapshot.services.size, serviceIds.size)
        assertEquals(snapshot.transactions.size, snapshot.transactions.map { it.id }.toSet().size)
        assertTrue(accountIds.all { it >= 1001 })
        assertTrue(serviceIds.all { it >= 1001 })
        assertTrue(snapshot.accounts.all { it.email.endsWith("@example.test") })
        assertTrue(snapshot.services.all { it.ownerId in accountIds })
        assertTrue(snapshot.transactions.all { it.serviceId in serviceIds })
        assertEquals(PaymentTerm.entries.toSet(), snapshot.services.map { it.paymentTerm }.toSet())
        assertEquals(ServiceStatus.entries.toSet(), snapshot.services.map { it.status }.toSet())

        val result = RiskEngine().calculate(snapshot, evaluationTime = evaluationTime)
        assertEquals(accountIds, result.customers.map { it.accountId }.toSet())
        assertEquals(serviceIds, result.services.map { it.serviceId }.toSet())
    }

    @Test
    fun `waiting cases isolate five distinct funding deficits without fixing score`() {
        val accounts = snapshot.accounts.associateBy { it.id }
        val cases = listOf(1017L, 1026L, 1027L, 1028L, 1016L)
        val waitingServices = cases.map { id -> snapshot.services.single { it.ownerId == id } }
        assertEquals(listOf(800L, 600L, 400L, 200L, 0L), cases.map { accounts.getValue(it).balance })
        assertEquals(setOf(800L), waitingServices.map { it.price }.toSet())
        assertEquals(setOf(ServiceStatus.PROLONG_WAIT), waitingServices.map { it.status }.toSet())
        assertEquals(setOf(Instant.parse("2026-10-18T15:00:00Z")), waitingServices.map { it.expiresAt }.toSet())
        assertTrue(cases.all { accounts.getValue(it).bonusBalance == 0L && accounts.getValue(it).withdrawBalance == 0L })
    }

    @Test
    fun `time boundaries and reversed input order are represented exactly`() {
        val services = snapshot.services.associateBy { it.id }
        val horizon = evaluationTime.plus(Duration.ofDays(7))
        assertEquals(listOf(-1L, 0L, 1L), listOf(2017L, 2018L, 2019L).map {
            Duration.between(horizon, services.getValue(it).expiresAt).seconds
        })
        assertEquals(listOf(1L, 0L, -1L), listOf(2020L, 2021L, 2022L).map {
            Duration.between(evaluationTime, services.getValue(it).expiresAt.plus(Duration.ofDays(5))).seconds
        })
        val tie = snapshot.services.filter { it.ownerId == 1018L }
        assertEquals(listOf(2026L, 2025L), tie.map { it.id })
        assertEquals(1, tie.map { it.expiresAt }.distinct().size)
        assertTrue(snapshot.services.none { it.ownerId == 1001L })
    }
}
