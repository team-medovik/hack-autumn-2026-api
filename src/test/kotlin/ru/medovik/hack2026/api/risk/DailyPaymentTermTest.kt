package ru.medovik.hack2026.api.risk

import ru.medovik.hack2026.api.dto.request.risk.PaymentTerm
import ru.medovik.hack2026.api.dto.request.risk.Service
import ru.medovik.hack2026.api.dto.request.risk.ServiceStatus
import ru.medovik.hack2026.api.service.risk.ExposureCalculator
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DailyPaymentTermTest {
    private val mapper = jacksonObjectMapper()
    private val calculator = ExposureCalculator()
    private val now = Instant.parse("2026-10-24T15:00:00Z")
    // Demonstration daily contract, not a confirmed public Aeza daily price.
    private val daily = Service(
        id = 90001, name = "synthetic-daily-vps", ownerId = 90001,
        paymentTerm = PaymentTerm.DAY, autoProlong = true,
        createdAt = now.minus(Duration.ofDays(60)), expiresAt = now,
        status = ServiceStatus.ACTIVE, price = 100,
    )

    @Test
    fun `day reads from JSON and round trips as a daily service`() {
        val json = """
            {
              "id": 90001, "name": "synthetic-daily-vps", "ownerId": 90001,
              "paymentTerm": "day", "autoProlong": true,
              "createdAt": "2026-08-25T15:00:00Z", "expiresAt": "2026-10-24T15:00:00Z",
              "status": "active", "price": 100
            }
        """.trimIndent()
        val service = mapper.readValue<Service>(json)
        assertEquals(PaymentTerm.DAY, service.paymentTerm)
        assertEquals("day", mapper.readTree(mapper.writeValueAsString(service)).get("paymentTerm").asText())
        assertEquals(service, mapper.readValue<Service>(mapper.writeValueAsString(service)))
    }

    @Test
    fun `seven day horizon includes both payment boundaries`() {
        val end = now.plus(Duration.ofDays(7))
        // A renewal due now is charged now and once on each of the next seven days.
        assertEquals(800L, calculator.calculate(daily, true, now, end).horizonPayments)
        assertEquals(700L, calculator.calculate(daily.copy(expiresAt = now.plusSeconds(86400)), true, now, end).horizonPayments)
        assertEquals(100L, calculator.calculate(daily.copy(expiresAt = end), true, now, end).horizonPayments)
        assertEquals(ExposureCalculator.Exposure(0, 0),
            calculator.calculate(daily.copy(expiresAt = end.plusNanos(1)), true, now, end))
    }

    @Test
    fun `daily step is exactly 86400 UTC seconds including fractional boundaries`() {
        val anchor = now.plusNanos(123456789)
        val service = daily.copy(expiresAt = anchor)
        val nextDay = anchor.plusSeconds(86400)
        assertEquals(100L, calculator.calculate(service, true, anchor, nextDay.minusNanos(1)).horizonPayments)
        assertEquals(200L, calculator.calculate(service, true, anchor, nextDay).horizonPayments)
        assertEquals(200L, calculator.calculate(service, true, anchor, nextDay.plusNanos(1)).horizonPayments)
        val sevenDays = anchor.plusSeconds(7 * 86400L)
        assertEquals(700L, calculator.calculate(service, true, anchor, sevenDays.minusNanos(1)).horizonPayments)
        assertEquals(800L, calculator.calculate(service, true, anchor, sevenDays).horizonPayments)
    }

    @Test
    fun `overdue daily forecast starts now without charging historical debt`() {
        val overdue = daily.copy(expiresAt = now.minus(Duration.ofDays(40)))
        assertEquals(ExposureCalculator.Exposure(100, 3000), calculator.calculate(overdue, true, now, now))
        assertEquals(ExposureCalculator.Exposure(200, 3000),
            calculator.calculate(overdue, true, now, now.plusSeconds(86400)))
        assertEquals(100L, calculator.calculate(overdue, true, now, now.plusSeconds(86400).minusNanos(1)).horizonPayments)
    }

    @Test
    fun `daily monthly loss is thirty prices with zero and exact long boundary`() {
        assertEquals(3000L, calculator.calculate(daily, true, now, now).potentialMonthlyLoss)
        assertEquals(30L, calculator.calculate(daily.copy(price = 1), true, now, now).potentialMonthlyLoss)
        assertEquals(ExposureCalculator.Exposure(0, 0),
            calculator.calculate(daily.copy(price = 0), true, now, now.plus(Duration.ofDays(7))))
        val largestSafePrice = Long.MAX_VALUE / 30
        assertEquals(largestSafePrice * 30,
            calculator.calculate(daily.copy(price = largestSafePrice), true, now, now).potentialMonthlyLoss)
        assertFailsWith<ArithmeticException> {
            calculator.calculate(daily.copy(price = largestSafePrice + 1), true, now, now)
        }
    }

    @Test
    fun `daily horizon overflow fails explicitly`() {
        assertFailsWith<ArithmeticException> {
            calculator.calculate(daily.copy(price = Long.MAX_VALUE), true, now, now.plusSeconds(86400))
        }
    }

    @Test
    fun `existing payment terms retain JSON names horizon amounts and normalization`() {
        val expected = listOf(
            Triple("hour", 141960L, 604800L),
            Triple("week", 1680L, 3600L),
            Triple("month", 840L, 840L),
            Triple("quarter_year", 840L, 280L),
            Triple("half_year", 840L, 140L),
            Triple("year", 840L, 70L),
        )
        for ((jsonName, horizonPayments, monthlyLoss) in expected) {
            val term = mapper.readValue<PaymentTerm>("\"$jsonName\"")
            assertEquals("\"$jsonName\"", mapper.writeValueAsString(term))
            val actual = calculator.calculate(daily.copy(price = 840, paymentTerm = term), true, now, now.plus(Duration.ofDays(7)))
            assertEquals(ExposureCalculator.Exposure(horizonPayments, monthlyLoss), actual, jsonName)
        }
    }
}
