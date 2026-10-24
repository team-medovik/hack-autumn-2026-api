package ru.medovik.hack2026.api.risk

import ru.medovik.hack2026.api.dto.request.risk.RiskSnapshot
import ru.medovik.hack2026.api.dto.request.risk.ServiceStatus
import ru.medovik.hack2026.api.dto.response.risk.RiskResult
import ru.medovik.hack2026.api.service.risk.RiskEngine
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServiceFundingTest {
    private val mapper = jacksonObjectMapper()
    private val snapshot = requireNotNull(javaClass.getResourceAsStream("/risk/extended-scenarios.json"))
        .use { mapper.readValue<RiskSnapshot>(it.readBytes()) }
    private val now = Instant.parse("2026-10-19T15:00:00Z")

    private fun calculate(input: RiskSnapshot = snapshot) = RiskEngine().calculate(input, evaluationTime = now)
    private fun RiskResult.feature(serviceId: Long, code: String) =
        services.single { it.serviceId == serviceId }.features.single { it.code == code }

    @Test
    fun `covered renewal has no F2 while later renewal uses its own shortfall`() {
        val result = calculate()
        val funding = result.customers.single { it.accountId == 207L }.funding
        assertEquals(1000L, funding.availableFunds)
        assertEquals(1600L, funding.requiredFunds)
        assertEquals(600L, funding.shortfall)
        assertEquals(0.375, funding.shortageStrength)
        assertEquals(listOf(307L, 308L), funding.renewals.map { it.serviceId })
        assertEquals(listOf(0L, 600L), funding.renewals.map { it.shortfall })
        assertEquals(listOf(1000L, 200L), funding.renewals.map { it.fundsBefore })
        assertEquals(0.0, result.feature(307, "F2").strength)
        assertEquals(0.75, result.feature(308, "F2").strength)
        assertEquals(30.0, result.feature(308, "F2").contribution)
        assertEquals("600", result.feature(308, "F2").evidence["serviceShortfall"])
        assertEquals("200", result.feature(308, "F2").evidence["fundsBefore"])
        assertEquals("0.75", result.feature(308, "F2").evidence["serviceShortageStrength"])

        val serialized = mapper.readTree(mapper.writeValueAsString(funding.renewals.last()))
        assertEquals(600L, serialized.get("shortfall").asLong())
        assertEquals(0.75, serialized.get("shortageStrength").asDouble())
        assertEquals("expert-f1-f5-v3", result.modelVersion)
    }

    @Test
    fun `same expiry uses lower ID and input ordering cannot change allocation`() {
        val result = calculate()
        val funding = result.customers.single { it.accountId == 211L }.funding
        assertEquals(listOf(312L, 313L), funding.renewals.map { it.serviceId })
        assertEquals(0.0, result.feature(312, "F2").strength)
        assertEquals(1.0, result.feature(313, "F2").strength)
        assertEquals(result, calculate(snapshot.copy(services = snapshot.services.reversed())))
    }

    @Test
    fun `failed renewal leaves funds for a later affordable renewal and zero price is safe`() {
        val result = calculate()
        val renewals = result.customers.single { it.accountId == 212L }.funding.renewals
        assertEquals(listOf(false, true, true), renewals.map { it.covered })
        assertEquals(listOf(200L, 200L, 0L), renewals.map { it.fundsBefore })
        assertEquals(listOf(200L, 0L, 0L), renewals.map { it.fundsAfter })
        assertEquals(listOf(100L, 0L, 0L), renewals.map { it.shortfall })
        assertEquals(1.0 / 3.0, result.feature(314, "F2").strength, 1e-12)
        assertEquals(0.0, result.feature(315, "F2").strength)
        assertEquals(0.0, result.feature(316, "F2").strength)
        assertTrue(renewals.all { it.shortageStrength.isFinite() && it.shortageStrength in 0.0..1.0 })
    }

    @Test
    fun `only F4 uses per-service shortage for awaiting renewals`() {
        val result = calculate()
        assertEquals(0.0, result.feature(317, "F2").contribution)
        assertEquals(0.0, result.feature(317, "F4").contribution)
        assertFalse(result.feature(317, "F2").applicable)
        assertFalse(result.feature(318, "F2").applicable)
        assertEquals(0.0, result.feature(318, "F2").contribution)
        assertEquals(0.75, result.feature(318, "F4").strength)
        assertEquals(22.5, result.feature(318, "F4").contribution)
        assertEquals("600", result.feature(318, "F4").evidence["serviceShortfall"])
    }

    @Test
    fun `locked bonus cannot cover renewal while unlocked bonus can`() {
        val result = calculate()
        assertEquals(1.0, result.feature(303, "F2").strength)
        assertEquals(0.0, result.feature(309, "F2").strength)
        assertEquals(1.0, result.feature(304, "F4").strength)
    }

    @Test
    fun `only live renewals through exact horizon boundary consume funds`() {
        val account = snapshot.accounts.single { it.id == 210L }.copy(balance = 500)
        val service = snapshot.services.single { it.id == 311L }
        val input = RiskSnapshot(
            accounts = listOf(account),
            services = listOf(
                service,
                service.copy(id = 901, expiresAt = service.expiresAt.plusSeconds(1)),
                service.copy(id = 902, status = ServiceStatus.DELETED),
                service.copy(id = 903, status = ServiceStatus.BLOCKED),
            ),
        )
        val result = calculate(input)
        assertEquals(listOf(311L), result.customers.single().funding.renewals.map { it.serviceId })
        assertEquals(0.0, result.feature(311, "F2").strength)
        assertFalse(result.feature(901, "F2").applicable)

        // Аномальный prolong_wait за горизонтом не получает чужую долю нехватки.
        val outside = calculate(input.copy(services = listOf(
            service.copy(status = ServiceStatus.PROLONG_WAIT, expiresAt = service.expiresAt.plusSeconds(1)),
        )))
        assertFalse(outside.feature(311, "F4").applicable)
    }
}
