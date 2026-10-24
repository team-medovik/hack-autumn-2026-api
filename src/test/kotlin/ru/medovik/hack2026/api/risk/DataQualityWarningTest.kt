package ru.medovik.hack2026.api.risk

import ru.medovik.hack2026.api.dto.request.risk.RiskSnapshot
import ru.medovik.hack2026.api.service.risk.RiskEngine
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DataQualityWarningTest {
    private val mapper = jacksonObjectMapper()
    private fun snapshot(name: String) = requireNotNull(javaClass.getResourceAsStream("/risk/$name"))
        .use { mapper.readValue<RiskSnapshot>(it.readBytes()) }

    @Test
    fun `original service 1 is scored with warning when its active status is stale`() {
        val input = snapshot("data.json")
        val result = RiskEngine().calculate(input, evaluationTime = Instant.parse("2026-10-24T12:00:00Z"))
        val service = result.services.single { it.serviceId == 1L }
        assertEquals(setOf("EXPIRED_LIVE_SERVICE"), service.dataQualityWarnings.map { it.code }.toSet())
        assertEquals(50.0, service.score)
        assertEquals(50.0, service.features.single { it.code == "F1" }.contribution)
        assertTrue(service.potentialMonthlyLoss > 0)
        assertTrue(result.customers.single { it.accountId == 1L }.explanations.any { it.serviceId == 1L })
        assertEquals(input.services.single { it.id == 1L }.status, service.status)
        val json = mapper.readTree(mapper.writeValueAsString(service))
        assertEquals(50.0, json.get("score").asDouble())
        assertEquals("EXPIRED_LIVE_SERVICE", json.get("dataQualityWarnings").get(0).get("code").asText())
    }

    @Test
    fun `original service 1 is not anomalous before its expiry at fixture reference time`() {
        val result = RiskEngine().calculate(snapshot("data.json"), evaluationTime = Instant.parse("2026-10-19T15:00:00Z"))
        val service = result.services.single { it.serviceId == 1L }
        assertTrue(service.dataQualityWarnings.isEmpty())
        assertEquals(49.851190476190474, service.score!!, 1e-10)
    }

    @Test
    fun `all supported temporal anomalies preserve scoring without extra penalty`() {
        val result = RiskEngine().calculate(snapshot("enriched-review-scenarios.json"),
            evaluationTime = Instant.parse("2026-10-19T15:00:00Z"))
        val cases = mapOf(
            2039L to "FUTURE_CREATED_AT",
            2040L to "EXPIRED_LIVE_SERVICE",
            2041L to "PROLONG_WAIT_BEFORE_EXPIRY",
            2022L to "PROLONG_WAIT_GRACE_EXCEEDED",
        )
        for ((id, warning) in cases) {
            val service = result.services.single { it.serviceId == id }
            assertTrue(service.dataQualityWarnings.any { it.code == warning })
            assertNotNull(service.score)
            assertEquals(service.features.sumOf { it.contribution }, service.rawScore)
            assertEquals(service.rawScore!!.coerceAtMost(100.0), service.score)
            assertEquals(result.settings.level(service.score), service.level)
        }
    }
}
