package ru.medovik.hack2026.api.risk

import ru.medovik.hack2026.api.dto.request.risk.RiskSnapshot
import ru.medovik.hack2026.api.service.risk.RiskEngine
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.security.MessageDigest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RiskFixtureTest {
    private val mapper = jacksonObjectMapper()
    private val evaluationTime = Instant.parse("2026-10-19T15:00:00Z")

    @Test
    fun `original case snapshot is preserved`() {
        val bytes = fixture("data.json")
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals("6e0e224671a4cd22059431b01fb6fed21e075100bb21b1545d94e746e666292e", digest)

        val snapshot = mapper.readValue<RiskSnapshot>(bytes)
        assertEquals(10, snapshot.accounts.size)
        assertEquals(30, snapshot.services.size)
        assertEquals(100, snapshot.transactions.size)
    }

    @Test
    fun `additional scenarios are valid input for the risk engine`() {
        val snapshot = mapper.readValue<RiskSnapshot>(fixture("extended-scenarios.json"))
        assertEquals(10, snapshot.accounts.size)
        assertEquals(11, snapshot.services.size)
        assertEquals(2, snapshot.transactions.size)

        val accountIds = snapshot.accounts.map { it.id }.toSet()
        val serviceIds = snapshot.services.map { it.id }.toSet()
        assertEquals(snapshot.accounts.size, accountIds.size)
        assertEquals(snapshot.services.size, serviceIds.size)
        assertEquals(snapshot.transactions.size, snapshot.transactions.map { it.id }.toSet().size)
        assertTrue(snapshot.services.all { it.ownerId in accountIds })
        assertTrue(snapshot.transactions.all { it.serviceId in serviceIds })

        // Сценарии: здоровое продление, F1, F2, prolong_wait без средств,
        // suspended, отсутствие оценки, общий кошелёк, бонус и аномальная дата.
        val result = RiskEngine().calculate(snapshot, evaluationTime = evaluationTime)
        assertEquals(accountIds, result.customers.map { it.accountId }.toSet())
        assertEquals(serviceIds, result.services.map { it.serviceId }.toSet())
    }

    private fun fixture(name: String): ByteArray = requireNotNull(
        javaClass.getResourceAsStream("/risk/$name")
    ) { "Missing risk fixture: $name" }.use { it.readBytes() }
}
