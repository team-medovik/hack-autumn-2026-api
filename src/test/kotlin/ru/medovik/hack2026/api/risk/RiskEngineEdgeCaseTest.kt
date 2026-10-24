package ru.medovik.hack2026.api.risk

import org.junit.jupiter.api.Test
import ru.medovik.hack2026.api.risk.model.*
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RiskEngineEdgeCaseTest {
    private val now = Instant.parse("2026-10-19T15:00:00Z")
    private val engine = RiskEngine()

    @Test
    fun `renewal coverage is chronological with stable ID tie break and failed charges preserve funds`() {
        val result = calculate(
            services = listOf(
                service(id = 2, price = 50, expiresAt = now.plusSeconds(20)),
                service(id = 3, price = 150, expiresAt = now.plusSeconds(10)),
                service(id = 1, price = 60, expiresAt = now.plusSeconds(20)),
            ),
            accounts = listOf(account(balance = 100)),
        )
        val funding = result.customers.single().funding

        assertEquals(260L, funding.requiredFunds)
        assertEquals(160L, funding.shortfall)
        assertEquals(160.0 / 260.0, funding.shortageStrength, 1e-12)
        assertEquals(listOf(3L, 1L, 2L), funding.renewals.map { it.serviceId })
        assertEquals(listOf(false, true, false), funding.renewals.map { it.covered })
        assertEquals(listOf(100L, 100L, 40L), funding.renewals.map { it.fundsBefore })
        assertEquals(listOf(100L, 40L, 40L), funding.renewals.map { it.fundsAfter })
        // Coverage order is explanatory; every due service uses the account shortage ratio.
        result.services.forEach {
            assertEquals(160.0 / 260.0, it.features.single { feature -> feature.code == "F2" }.strength, 1e-12)
        }
    }

    @Test
    fun `available balance overflow fails explicitly instead of creating negative funds`() {
        assertFailsWith<ArithmeticException> {
            calculate(
                listOf(service()),
                listOf(account(balance = Long.MAX_VALUE).copy(bonusBalance = 1, bonusState = BonusState.UNLOCKED)),
            )
        }
    }

    @Test
    fun `total renewal price overflow fails explicitly instead of hiding shortage`() {
        assertFailsWith<ArithmeticException> {
            calculate(listOf(service(id = 1, price = Long.MAX_VALUE), service(id = 2, price = 1)))
        }
    }

    @Test
    fun `overdue active and suspended warnings do not add points to their feature scores`() {
        val result = calculate(listOf(
            service(id = 1, autoProlong = false, expiresAt = now.minusSeconds(1)),
            service(id = 2, status = ServiceStatus.SUSPENDED, autoProlong = false, expiresAt = now.minusSeconds(1)),
        ))

        assertEquals(50.0, result.service(1).score)
        assertEquals(65.0, result.service(2).score)
        result.services.forEach { risk ->
            assertTrue(risk.dataQualityWarnings.any { it.code == "EXPIRED_LIVE_SERVICE" })
            assertEquals(risk.features.sumOf { it.contribution }, risk.rawScore)
        }
    }

    @Test
    fun `wait grace warning begins at five days while premature wait is separately reported`() {
        val result = calculate(listOf(
            service(id = 1, status = ServiceStatus.PROLONG_WAIT, expiresAt = now.minus(Duration.ofDays(5))),
            service(id = 2, status = ServiceStatus.PROLONG_WAIT, expiresAt = now.minus(Duration.ofDays(5)).plusSeconds(1)),
            service(id = 3, status = ServiceStatus.PROLONG_WAIT, expiresAt = now.plus(Duration.ofDays(1))),
        ))

        assertTrue(result.service(1).dataQualityWarnings.any { it.code == "PROLONG_WAIT_GRACE_EXCEEDED" })
        assertFalse(result.service(2).dataQualityWarnings.any { it.code == "PROLONG_WAIT_GRACE_EXCEEDED" })
        assertTrue(result.service(3).dataQualityWarnings.any { it.code == "PROLONG_WAIT_BEFORE_EXPIRY" })
        result.services.forEach { assertEquals(70.0, it.score) }
        assertEquals(now, result.service(1).nextCriticalAt)
        assertEquals(now.plusSeconds(1), result.service(2).nextCriticalAt)
        assertEquals(now.plus(Duration.ofDays(6)), result.service(3).nextCriticalAt)
    }

    @Test
    fun `exposure sums one renewal per at risk due service without extrapolating hourly prices`() {
        val soon = now.plus(Duration.ofHours(1))
        val result = calculate(listOf(
            service(id = 1, price = 95, autoProlong = false, expiresAt = soon).copy(paymentTerm = PaymentTerm.HOUR),
            service(id = 2, price = 105, status = ServiceStatus.PROLONG_WAIT, expiresAt = now.minus(Duration.ofDays(2))),
            service(id = 3, price = 900, expiresAt = now.plus(Duration.ofDays(20))),
            service(id = 4, price = 800, status = ServiceStatus.BLOCKED, expiresAt = now),
        ))

        assertEquals(95L, result.service(1).renewalPrice)
        assertEquals(95L, result.service(1).revenueExposure)
        assertEquals(105L, result.service(2).revenueExposure)
        assertEquals(0L, result.service(3).revenueExposure)
        assertEquals(0L, result.service(4).revenueExposure)
        assertEquals(soon, result.service(1).nextCriticalAt)
        assertEquals(now.plus(Duration.ofDays(3)), result.service(2).nextCriticalAt)
        assertNull(result.service(4).nextCriticalAt)
        val customer = result.customers.single()
        assertEquals(200L, customer.revenueExposure)
        assertEquals(200L, customer.funding.requiredFunds)
        assertEquals(2, customer.servicesAtRisk)
        assertEquals(2.0 / 3.0, customer.portfolioShareAtRisk, 1e-12)
        assertEquals(soon, customer.nearestRiskAt)
        assertEquals(70.0, customer.attentionScore)
    }

    @Test
    fun `above threshold risk outside horizon remains visible but has zero renewal exposure`() {
        val deadline = now.plus(Duration.ofDays(20))
        val result = calculate(
            listOf(service(status = ServiceStatus.SUSPENDED, expiresAt = deadline, price = 1_000)),
            settings = RiskSettings(weights = RiskSettings().weights + ("F5" to 50.0)),
        )

        assertEquals(50.0, result.service(1).score)
        assertEquals(0L, result.service(1).revenueExposure)
        assertEquals(1, result.customers.single().servicesAtRisk)
        assertEquals(deadline, result.customers.single().nearestRiskAt)
        assertEquals(0L, result.customers.single().revenueExposure)
        assertEquals(0L, result.customers.single().funding.requiredFunds)
    }

    @Test
    fun `reordering input accounts and services does not change the complete result`() {
        val snapshot = RiskSnapshot(
            accounts = listOf(account(id = 2, balance = 0), account(id = 1)),
            services = listOf(
                service(id = 4, ownerId = 2, status = ServiceStatus.PROLONG_WAIT),
                service(id = 1, autoProlong = false, expiresAt = now),
                service(id = 3, ownerId = 2, status = ServiceStatus.SUSPENDED),
                service(id = 2, status = ServiceStatus.PROLONG_WAIT),
            ),
        )
        val original = engine.calculate(snapshot = snapshot, evaluationTime = now)
        val reordered = engine.calculate(
            snapshot = snapshot.copy(accounts = snapshot.accounts.reversed(), services = snapshot.services.reversed()),
            evaluationTime = now,
        )

        assertEquals(original, reordered)
        assertEquals(listOf(1L, 2L, 3L, 4L), original.services.map { it.serviceId })
        assertEquals(listOf(1L, 2L), original.customers.map { it.accountId })
    }

    private fun calculate(
        services: List<Service>,
        accounts: List<Account> = listOf(account()),
        settings: RiskSettings = RiskSettings(),
    ) = engine.calculate(
        snapshot = RiskSnapshot(accounts = accounts, services = services),
        settings = settings,
        evaluationTime = now,
    )

    private fun account(id: Long = 1, balance: Long = 100_000) = Account(
        id = id,
        email = "client-$id@example.test",
        balance = balance,
        withdrawBalance = 0,
        totalReplenished = 0,
        bonusBalance = 0,
        bonusState = BonusState.LOCKED,
    )

    private fun service(
        id: Long = 1,
        ownerId: Long = 1,
        price: Long = 100,
        status: ServiceStatus = ServiceStatus.ACTIVE,
        autoProlong: Boolean = true,
        expiresAt: Instant = now.plus(Duration.ofDays(1)),
    ) = Service(
        id = id,
        name = "Service $id",
        ownerId = ownerId,
        paymentTerm = PaymentTerm.MONTH,
        autoProlong = autoProlong,
        createdAt = now.minus(Duration.ofDays(365)),
        expiresAt = expiresAt,
        status = status,
        price = price,
    )

    private fun RiskResult.service(id: Long) = services.single { it.serviceId == id }
}
