package ru.medovik.hack2026.api.risk

import ru.medovik.hack2026.api.dto.request.risk.PaymentTerm
import ru.medovik.hack2026.api.dto.request.risk.RiskSnapshot
import ru.medovik.hack2026.api.dto.request.risk.ServiceStatus
import ru.medovik.hack2026.api.service.risk.ExposureCalculator
import ru.medovik.hack2026.api.service.risk.RiskEngine
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExposureAndWaitingTest {
    private val mapper = jacksonObjectMapper()
    private val snapshot = requireNotNull(javaClass.getResourceAsStream("/risk/enriched-review-scenarios.json"))
        .use { mapper.readValue<RiskSnapshot>(it.readBytes()) }
    private val now = Instant.parse("2026-10-19T15:00:00Z")
    private val result = RiskEngine().calculate(snapshot, evaluationTime = now)
    private val calculator = ExposureCalculator()
    private val base = snapshot.services.single { it.id == 2009L }

    @Test
    fun `five waiting deficits produce distinct F3 plus F4 scores`() {
        val cases = listOf(1017L, 1026L, 1027L, 1028L, 1016L)
        val services = cases.map { id -> result.services.single { it.ownerId == id } }
        assertEquals(listOf(70.0, 77.5, 85.0, 92.5, 100.0), services.map { it.score })
        assertEquals(services.map { it.score }, services.map { it.rawScore })
        services.forEach { service ->
            assertFalse(service.features.single { it.code == "F2" }.applicable)
            assertTrue(service.features.filter { it.contribution > 0 }.all { it.code in setOf("F3", "F4") })
        }
    }

    @Test
    fun `all six tariffs have horizon payments and normalized monthly loss`() {
        val services = result.services.filter { it.ownerId == 1020L }
        assertEquals(listOf(1680L, 700L, 3000L, 9000L, 18000L, 36000L), services.map { it.horizonPaymentExposure })
        assertEquals(listOf(7200L, 3000L, 3000L, 3000L, 3000L, 3000L), services.map { it.potentialMonthlyLoss })
        val customer = result.customers.single { it.accountId == 1020L }
        assertEquals(68380L, customer.horizonPaymentExposure)
        assertEquals(22200L, customer.potentialMonthlyLoss)
    }

    @Test
    fun `response exposes monthly loss but neither horizon exposure nor old ambiguous field`() {
        val tree = mapper.readTree(mapper.writeValueAsString(result))
        for (group in listOf("services", "customers")) {
            tree.get(group).forEach { node ->
                assertTrue(node.has("potentialMonthlyLoss"))
                assertFalse(node.has("horizonPaymentExposure"))
                assertFalse(node.has("revenueExposure"))
            }
        }
    }

    @Test
    fun `unfunded renewal does not reserve balance from later cheap renewal`() {
        val funding = result.customers.single { it.accountId == 1019L }.funding
        assertEquals(listOf(false, true, true), funding.renewals.map { it.covered })
        assertEquals(listOf(300L, 300L, 0L), funding.renewals.map { it.fundsBefore })
        assertEquals(listOf(300L, 0L, 0L), funding.renewals.map { it.fundsAfter })
    }

    @Test
    fun `low risk inactive and outside horizon do not contribute losses`() {
        assertEquals(0L, result.services.single { it.serviceId == 2009L }.potentialMonthlyLoss)
        val end = now.plus(Duration.ofDays(7))
        for (status in listOf(ServiceStatus.BLOCKED, ServiceStatus.DELETED)) {
            assertEquals(ExposureCalculator.Exposure(0, 0), calculator.calculate(base.copy(status = status), true, now, end))
        }
        assertEquals(ExposureCalculator.Exposure(0, 0), calculator.calculate(base, false, now, end))
        assertEquals(ExposureCalculator.Exposure(0, 0), calculator.calculate(base.copy(expiresAt = end.plusNanos(1)), true, now, end))
        assertEquals(800L, calculator.calculate(base.copy(expiresAt = end), true, now, end).horizonPayments)
    }

    @Test
    fun `overdue hourly forecast starts now without charging historical debt`() {
        val service = base.copy(paymentTerm = PaymentTerm.HOUR, expiresAt = now.minus(Duration.ofDays(40)),
            createdAt = now.minus(Duration.ofDays(50)), price = 10)
        val exposure = calculator.calculate(service, true, now, now.plusSeconds(3600))
        assertEquals(20L, exposure.horizonPayments) // now and now + 1 hour
        assertEquals(7200L, exposure.potentialMonthlyLoss)
        assertEquals(10L, calculator.calculate(service, true, now, now.plusSeconds(3600).minusNanos(1)).horizonPayments)
    }

    @Test
    fun `calendar periods preserve end of month anchor and leap dates`() {
        val anchor = Instant.parse("2028-01-31T15:00:00Z")
        val service = base.copy(expiresAt = anchor, paymentTerm = PaymentTerm.MONTH)
        val febEnd = Instant.parse("2028-02-29T15:00:00Z")
        assertEquals(1600L, calculator.calculate(service, true, anchor, febEnd).horizonPayments)
        assertEquals(800L, calculator.calculate(service, true, anchor, febEnd.minusNanos(1)).horizonPayments)
        assertEquals(1600L, calculator.calculate(service, true, anchor, Instant.parse("2028-03-30T15:00:00Z")).horizonPayments)
        assertEquals(2400L, calculator.calculate(service, true, anchor, Instant.parse("2028-03-31T15:00:00Z")).horizonPayments)
        for ((term, months) in listOf(PaymentTerm.QUARTER_YEAR to 3L, PaymentTerm.HALF_YEAR to 6L, PaymentTerm.YEAR to 12L)) {
            val end = anchor.atZone(java.time.ZoneOffset.UTC).plusMonths(months).toInstant()
            assertEquals(1600L, calculator.calculate(service.copy(paymentTerm = term), true, anchor, end).horizonPayments)
        }
    }

    @Test
    fun `zero fractional cents and overflow are handled explicitly`() {
        val end = now.plus(Duration.ofDays(7))
        assertEquals(ExposureCalculator.Exposure(0, 0), calculator.calculate(base.copy(price = 0), true, now, end))
        assertEquals(4L, calculator.calculate(base.copy(price = 1, paymentTerm = PaymentTerm.WEEK), true, now, end).potentialMonthlyLoss)
        assertEquals(1L, calculator.calculate(base.copy(price = 6, paymentTerm = PaymentTerm.YEAR), true, now, end).potentialMonthlyLoss)
        assertFailsWith<ArithmeticException> {
            calculator.calculate(base.copy(price = Long.MAX_VALUE, paymentTerm = PaymentTerm.HOUR), true, now, end)
        }
    }
}
