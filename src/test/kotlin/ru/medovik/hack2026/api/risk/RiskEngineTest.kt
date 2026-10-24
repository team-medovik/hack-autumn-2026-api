package ru.medovik.hack2026.api.risk

import org.junit.jupiter.api.Test
import ru.medovik.hack2026.api.risk.model.*
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RiskEngineTest {
    private val now = Instant.parse("2026-10-19T15:00:00Z")
    private val engine = RiskEngine()

    @Test
    fun `F1 strength is one half at three and a half days`() {
        val result = calculate(listOf(service(expiresAt = now.plus(Duration.ofHours(84)), autoProlong = false)))

        result.service(1).assertFeature("F1", strength = 0.5, contribution = 25.0)
        assertEquals(25.0, result.service(1).score)
        assertEquals(RiskLevel.LOW, result.service(1).level)
        assertEquals(75.0, result.service(1).loyaltyScore)
    }

    @Test
    fun `F1 horizon includes its exact boundary but not a later expiry`() {
        val result = calculate(listOf(
            service(id = 1, autoProlong = false, expiresAt = now.plus(Duration.ofDays(7))),
            service(id = 2, autoProlong = false, expiresAt = now.plus(Duration.ofDays(7)).plusSeconds(1)),
        ))

        result.service(1).assertFeature("F1", strength = 0.0, contribution = 0.0)
        assertTrue(result.service(1).feature("F1").applicable)
        assertFalse(result.service(2).feature("F1").applicable)
        assertEquals(100L, result.customers.single().funding.requiredFunds)
    }

    @Test
    fun `F1 reaches full strength exactly at expiry and stays full when overdue`() {
        val result = calculate(listOf(
            service(id = 1, autoProlong = false, expiresAt = now),
            service(id = 2, autoProlong = false, expiresAt = now.minus(Duration.ofDays(10))),
        ))

        for (id in listOf(1L, 2L)) {
            result.service(id).assertFeature("F1", strength = 1.0, contribution = 50.0)
            assertEquals(50.0, result.service(id).score)
            assertEquals(RiskLevel.MEDIUM, result.service(id).level)
        }
    }

    @Test
    fun `F1 does not apply with auto renewal or to awaiting payment`() {
        val result = calculate(listOf(
            service(id = 1, autoProlong = true, expiresAt = now),
            service(id = 2, status = ServiceStatus.PROLONG_WAIT, autoProlong = false, expiresAt = now),
        ))

        assertFalse(result.service(1).feature("F1").applicable)
        assertFalse(result.service(2).feature("F1").applicable)
        assertEquals(0.0, result.service(1).score)
        assertEquals(70.0, result.service(2).score)
    }

    @Test
    fun `F2 shares partial account shortage across all due live services regardless of auto renewal`() {
        val result = calculate(
            services = listOf(
                service(id = 1, price = 100, autoProlong = true),
                service(id = 2, price = 300, autoProlong = false, status = ServiceStatus.SUSPENDED),
                service(id = 3, price = 900, expiresAt = now.plus(Duration.ofDays(8))),
                service(id = 4, price = 900, status = ServiceStatus.DELETED),
                service(id = 5, price = 900, status = ServiceStatus.BLOCKED),
            ),
            accounts = listOf(account(balance = 200)),
        )

        val funding = result.customers.single().funding
        assertEquals(400L, funding.requiredFunds)
        assertEquals(200L, funding.availableFunds)
        assertEquals(200L, funding.shortfall)
        assertEquals(0.5, funding.shortageStrength)
        for (id in listOf(1L, 2L)) {
            result.service(id).assertFeature("F2", strength = 0.5, contribution = 20.0)
        }
        result.service(3).assertFeature("F2", strength = 0.0, contribution = 0.0)
        assertFalse(result.service(3).feature("F2").applicable)
        assertEquals(setOf(1L, 2L), funding.renewals.map { it.serviceId }.toSet())
    }

    @Test
    fun `F2 includes overdue renewals and excludes other account balances`() {
        val result = calculate(
            services = listOf(
                service(id = 1, ownerId = 1, expiresAt = now.minus(Duration.ofDays(30)), price = 100),
                service(id = 2, ownerId = 2, price = 100),
            ),
            accounts = listOf(account(id = 1, balance = 0), account(id = 2, balance = 1_000)),
        )

        result.service(1).assertFeature("F2", strength = 1.0, contribution = 40.0)
        result.service(2).assertFeature("F2", strength = 0.0, contribution = 0.0)
        assertEquals(RiskLevel.MEDIUM, result.service(1).level)
        assertEquals(100L, result.customers.single { it.accountId == 1L }.funding.requiredFunds)
    }

    @Test
    fun `only unlocked bonus contributes to funds and withdrawal balance never does`() {
        val locked = account(balance = 25, bonusBalance = 75, bonusState = BonusState.LOCKED, withdrawBalance = 10_000)
        val unlocked = locked.copy(bonusState = BonusState.UNLOCKED)
        val lockedResult = calculate(listOf(service(price = 100)), listOf(locked))
        val unlockedResult = calculate(listOf(service(price = 100)), listOf(unlocked))

        assertEquals(25L, lockedResult.customers.single().funding.availableFunds)
        lockedResult.service(1).assertFeature("F2", strength = 0.75, contribution = 30.0)
        assertEquals(100L, unlockedResult.customers.single().funding.availableFunds)
        unlockedResult.service(1).assertFeature("F2", strength = 0.0, contribution = 0.0)
    }

    @Test
    fun `F3 awaiting payment with sufficient funds is exactly seventy`() {
        val result = calculate(listOf(service(status = ServiceStatus.PROLONG_WAIT, autoProlong = false)))
        val risk = result.service(1)

        risk.assertFeature("F3", strength = 1.0, contribution = 70.0)
        risk.assertFeature("F4", strength = 0.0, contribution = 0.0)
        assertEquals(70.0, risk.rawScore)
        assertEquals(70.0, risk.score)
        assertEquals(RiskLevel.HIGH, risk.level)
        assertEquals(30.0, risk.loyaltyScore)
    }

    @Test
    fun `awaiting payment without money adds F2 F3 and F4 before capping at one hundred`() {
        val result = calculate(
            listOf(service(status = ServiceStatus.PROLONG_WAIT, expiresAt = now.minusSeconds(1))),
            listOf(account(balance = 0)),
        )
        val risk = result.service(1)

        risk.assertFeature("F2", strength = 1.0, contribution = 40.0)
        risk.assertFeature("F3", strength = 1.0, contribution = 70.0)
        risk.assertFeature("F4", strength = 1.0, contribution = 30.0)
        assertEquals(140.0, risk.rawScore)
        assertEquals(100.0, risk.score)
        assertEquals(0.0, risk.loyaltyScore)
        assertEquals(RiskLevel.HIGH, result.customers.single().level)
    }

    @Test
    fun `F4 uses the same fractional shortage as F2`() {
        val result = calculate(
            listOf(service(status = ServiceStatus.PROLONG_WAIT, price = 400)),
            listOf(account(balance = 300)),
        )

        result.service(1).assertFeature("F2", strength = 0.25, contribution = 10.0)
        result.service(1).assertFeature("F4", strength = 0.25, contribution = 7.5)
        assertEquals(87.5, result.service(1).score)
    }

    @Test
    fun `suspended alone scores fifteen while overdue without auto renewal scores sixty five`() {
        val result = calculate(listOf(
            service(id = 1, status = ServiceStatus.SUSPENDED, expiresAt = now.plus(Duration.ofDays(20))),
            service(id = 2, status = ServiceStatus.SUSPENDED, autoProlong = false, expiresAt = now.minus(Duration.ofDays(1))),
        ))

        result.service(1).assertFeature("F5", strength = 1.0, contribution = 15.0)
        assertEquals(15.0, result.service(1).score)
        assertEquals(RiskLevel.LOW, result.service(1).level)
        assertEquals(65.0, result.service(2).score)
        assertEquals(RiskLevel.MEDIUM, result.service(2).level)
    }

    @Test
    fun `blocked and deleted services have no score and do not raise customer risk`() {
        val result = calculate(listOf(
            service(id = 1, status = ServiceStatus.BLOCKED, autoProlong = false, expiresAt = now),
            service(id = 2, status = ServiceStatus.DELETED, autoProlong = false, expiresAt = now),
            service(id = 3, expiresAt = now.plus(Duration.ofDays(20))),
        ), listOf(account(balance = 0)))

        for (id in listOf(1L, 2L)) {
            assertNull(result.service(id).score)
            assertNull(result.service(id).rawScore)
            assertNull(result.service(id).loyaltyScore)
            assertEquals(RiskLevel.NONE, result.service(id).level)
        }
        assertEquals(0.0, result.customers.single().attentionScore)
        assertEquals(1, result.customers.single().liveServices)
        assertEquals(0L, result.customers.single().funding.requiredFunds)
    }

    @Test
    fun `customer attention is maximum live service score instead of average or sum`() {
        val result = calculate(listOf(
            service(id = 1, status = ServiceStatus.PROLONG_WAIT),
            service(id = 2, status = ServiceStatus.SUSPENDED, expiresAt = now.plus(Duration.ofDays(20))),
            service(id = 3, expiresAt = now.plus(Duration.ofDays(20))),
        ))

        val customer = result.customers.single()
        assertEquals(70.0, customer.attentionScore)
        assertEquals(RiskLevel.HIGH, customer.level)
        assertEquals(30.0, customer.loyaltyScore)
        assertEquals(3, customer.liveServices)
        assertEquals(1, customer.servicesAtRisk)
        assertEquals(1.0 / 3.0, customer.portfolioShareAtRisk, 1e-12)
    }

    @Test
    fun `customers without live services receive none including a mixed deleted blocked account`() {
        val result = calculate(
            services = listOf(
                service(id = 1, ownerId = 2, status = ServiceStatus.DELETED),
                service(id = 2, ownerId = 3, status = ServiceStatus.BLOCKED),
                service(id = 3, ownerId = 4, status = ServiceStatus.DELETED),
                service(id = 4, ownerId = 4, status = ServiceStatus.BLOCKED),
            ),
            accounts = (1L..4L).map { account(id = it) },
        )

        assertEquals(4, result.customers.size)
        result.customers.forEach { customer ->
            assertNull(customer.attentionScore)
            assertNull(customer.loyaltyScore)
            assertEquals(RiskLevel.NONE, customer.level)
            assertEquals(0, customer.liveServices)
            assertEquals(0, customer.servicesAtRisk)
        }
    }

    @Test
    fun `custom weights and thresholds change contributions and levels`() {
        val settings = RiskSettings(
            weights = RiskSettings().weights + ("F3" to 20.0),
            mediumThreshold = 15.0,
            highThreshold = 20.0,
        )
        val result = calculate(listOf(service(status = ServiceStatus.PROLONG_WAIT)), settings = settings)

        result.service(1).assertFeature("F3", strength = 1.0, contribution = 20.0)
        assertEquals(20.0, result.service(1).score)
        assertEquals(RiskLevel.HIGH, result.service(1).level)
        assertEquals(settings, result.settings)
    }

    @Test
    fun `custom horizon changes both expiry urgency and shared funding requirements`() {
        val services = listOf(service(expiresAt = now.plus(Duration.ofDays(5)), autoProlong = false))
        val shorter = calculate(services, listOf(account(balance = 0)), RiskSettings(horizonDays = 4))
        val longer = calculate(services, listOf(account(balance = 0)), RiskSettings(horizonDays = 10))

        assertEquals(0.0, shorter.service(1).score)
        assertEquals(0L, shorter.customers.single().funding.requiredFunds)
        longer.service(1).assertFeature("F1", strength = 0.5, contribution = 25.0)
        longer.service(1).assertFeature("F2", strength = 1.0, contribution = 40.0)
        assertEquals(65.0, longer.service(1).score)
    }

    @Test
    fun `zero configured weights remove even awaiting payment risk without implicit overrides`() {
        val settings = RiskSettings(weights = RiskSettings().weights.mapValues { 0.0 })
        val result = calculate(
            listOf(service(status = ServiceStatus.PROLONG_WAIT, expiresAt = now)),
            listOf(account(balance = 0)),
            settings,
        )

        assertEquals(0.0, result.service(1).rawScore)
        assertEquals(0.0, result.service(1).score)
        assertEquals(RiskLevel.LOW, result.service(1).level)
        assertEquals(100.0, result.service(1).loyaltyScore)
    }

    @Test
    fun `transactions and display fields do not change risk`() {
        val originalAccount = account()
        val originalService = service(autoProlong = false, expiresAt = now)
        val baseline = calculate(listOf(originalService), listOf(originalAccount))
        val alternative = engine.calculate(
            snapshot = RiskSnapshot(
                accounts = listOf(originalAccount.copy(email = "renamed@example.test", totalReplenished = 5_000_000)),
                services = listOf(originalService.copy(name = "Renamed", paymentTerm = PaymentTerm.YEAR)),
                transactions = listOf(transaction()),
            ),
            evaluationTime = now,
        )

        assertEquals(baseline.service(1).score, alternative.service(1).score)
        assertEquals(baseline.service(1).features.map { it.strength }, alternative.service(1).features.map { it.strength })
        assertEquals(baseline.customers.single().attentionScore, alternative.customers.single().attentionScore)
    }

    @Test
    fun `zero price due services produce zero shortage instead of division by zero`() {
        val result = calculate(listOf(service(price = 0)), listOf(account(balance = 0)))

        assertEquals(0L, result.customers.single().funding.requiredFunds)
        assertEquals(0.0, result.customers.single().funding.shortageStrength)
        assertEquals(0.0, result.service(1).score)
    }

    @Test
    fun `fixed evaluation time is deterministic and input collections are unchanged`() {
        val accounts = mutableListOf(account(id = 2), account(id = 1, balance = 0))
        val services = mutableListOf(
            service(id = 2, ownerId = 2, status = ServiceStatus.SUSPENDED),
            service(id = 3, ownerId = 1, status = ServiceStatus.PROLONG_WAIT),
            service(id = 1, ownerId = 1, expiresAt = now.minus(Duration.ofDays(3))),
        )
        val snapshot = RiskSnapshot(accounts, services)
        val originalAccounts = accounts.toList()
        val originalServices = services.toList()

        val first = engine.calculate(snapshot = snapshot, evaluationTime = now)
        val second = engine.calculate(snapshot = snapshot, evaluationTime = now)

        assertEquals(first, second)
        assertEquals(originalAccounts, accounts)
        assertEquals(originalServices, services)
        assertEquals(now, first.evaluationTime)
        first.services.filter { it.score != null }.forEach { risk ->
            assertEquals(risk.features.sumOf { it.contribution }, assertNotNull(risk.rawScore), 1e-9)
            assertEquals(assertNotNull(risk.rawScore).coerceAtMost(100.0), assertNotNull(risk.score), 1e-9)
            assertTrue(risk.features.all { it.strength in 0.0..1.0 })
            assertTrue(risk.features.all { it.explanation.isNotBlank() })
        }
    }

    @Test
    fun `invalid model settings are rejected`() {
        assertFailsWith<IllegalArgumentException> { RiskSettings(horizonDays = 0) }
        assertFailsWith<IllegalArgumentException> { RiskSettings(horizonDays = -1) }
        for (weight in listOf(-1.0, 101.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertFailsWith<IllegalArgumentException> { RiskSettings(weights = RiskSettings().weights + ("F1" to weight)) }
        }
        assertFailsWith<IllegalArgumentException> { RiskSettings(mediumThreshold = 70.0, highThreshold = 70.0) }
        assertFailsWith<IllegalArgumentException> { RiskSettings(mediumThreshold = 80.0, highThreshold = 70.0) }
        assertFailsWith<IllegalArgumentException> { RiskSettings(highThreshold = 101.0) }
        assertFailsWith<IllegalArgumentException> { RiskSettings(mediumThreshold = Double.NaN) }
    }

    @Test
    fun `duplicate account and service identifiers are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            calculate(listOf(service()), listOf(account(), account()))
        }
        assertFailsWith<IllegalArgumentException> {
            calculate(listOf(service(), service()))
        }
    }

    @Test
    fun `service without owning account is rejected`() {
        assertFailsWith<IllegalArgumentException> { calculate(listOf(service(ownerId = 2))) }
    }

    @Test
    fun `duplicate transactions and transactions without a service are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            engine.calculate(
                snapshot = RiskSnapshot(listOf(account()), listOf(service()), listOf(transaction(), transaction())),
                evaluationTime = now,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            engine.calculate(
                snapshot = RiskSnapshot(listOf(account()), listOf(service()), listOf(transaction(serviceId = 2))),
                evaluationTime = now,
            )
        }
    }

    private fun calculate(
        services: List<Service>,
        accounts: List<Account> = listOf(account()),
        settings: RiskSettings = RiskSettings(),
    ): RiskResult = engine.calculate(
        snapshot = RiskSnapshot(accounts = accounts, services = services),
        settings = settings,
        evaluationTime = now,
    )

    private fun account(
        id: Long = 1,
        balance: Long = 100_000,
        bonusBalance: Long = 0,
        bonusState: BonusState = BonusState.LOCKED,
        withdrawBalance: Long = 0,
    ) = Account(
        id = id,
        email = "client-$id@example.test",
        balance = balance,
        withdrawBalance = withdrawBalance,
        totalReplenished = 0,
        bonusBalance = bonusBalance,
        bonusState = bonusState,
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

    private fun transaction(serviceId: Long = 1) = Transaction(
        id = 1,
        amount = 100,
        status = TransactionStatus.NOT_PERFORMED,
        performedAt = null,
        createdAt = now.minus(Duration.ofDays(1)),
        type = TransactionType.PROLONG,
        serviceId = serviceId,
    )

    private fun RiskResult.service(id: Long): ServiceRiskResult = services.single { it.serviceId == id }

    private fun ServiceRiskResult.feature(code: String): FeatureContribution = features.single { it.code == code }

    private fun ServiceRiskResult.assertFeature(code: String, strength: Double, contribution: Double) {
        val feature = feature(code)
        assertEquals(strength, feature.strength, 1e-9, "$code strength for service $serviceId")
        assertEquals(contribution, feature.contribution, 1e-9, "$code contribution for service $serviceId")
        assertEquals(feature.strength * feature.weight, feature.contribution, 1e-9)
    }
}
