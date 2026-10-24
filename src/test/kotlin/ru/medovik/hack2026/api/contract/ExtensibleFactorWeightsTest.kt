package ru.medovik.hack2026.api.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.assertThrows
import ru.medovik.hack2026.api.dto.settings.FactorWeights
import ru.medovik.hack2026.api.dto.settings.ModelSettingsChanges
import ru.medovik.hack2026.api.dto.settings.ModelSettingsValues
import ru.medovik.hack2026.api.dto.settings.PatchFactorWeights
import ru.medovik.hack2026.api.dto.settings.RiskThresholds
import ru.medovik.hack2026.api.dto.settings.FinanceSettings
import ru.medovik.hack2026.api.dto.settings.ForecastSettings
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

/**
 * Guard OpenAPI FactorWeights.additionalProperties:
 * new feature keys are peer properties, not wrapped under additionalWeights.
 */
class ExtensibleFactorWeightsTest {
    private val json = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    @Test
    fun `new registered weight roundtrips as sibling property in JSON`() {
        val initial = FactorWeights(
            awaitingPayment = 70,
            expiringWithoutAutoRenewal = 50,
            insufficientFunds = 40,
            awaitingPaymentWithoutFunds = 30,
            disabledByClient = 15,
            bonusesExceedMoney = 10,
        )
        initial.putAdditionalWeight("experimentalFeature", 85)
        val wire = json.writeValueAsString(initial)
        assertTrue(wire.contains("\"experimentalFeature\":85"), wire)
        assertFalse(wire.contains("\"additionalWeights\""), wire)
        val decoded = json.readValue(wire, FactorWeights::class.java)
        assertEquals(85, decoded.additionalWeights["experimentalFeature"])
        assertEquals(70, decoded.awaitingPayment)
    }

    @Test
    fun `partial model update retains old registered weights`() {
        val values = ModelSettingsValues(
            factorWeights = FactorWeights(70, 50, 40, 30, 15, 10, linkedMapOf("oldFeature" to 42)),
            riskThresholds = RiskThresholds(40, 70),
            finance = FinanceSettings(true, false, 0.5),
            forecast = ForecastSettings(5, 5),
        )
        val updated = ModelSettingsChanges(
            factorWeights = PatchFactorWeights(additionalWeights = mapOf("newFeature" to 80)),
        ).mergeInto(values)
        assertEquals(42, updated.factorWeights.additionalWeights["oldFeature"])
        assertEquals(80, updated.factorWeights.additionalWeights["newFeature"])
        assertEquals(70, updated.factorWeights.awaitingPayment)
    }

    @Test
    fun `unknown weight outside OpenAPI range is rejected`() {
        val initial = FactorWeights(70, 50, 40, 30, 15, 10)
        assertThrows<IllegalArgumentException> { initial.putAdditionalWeight("badFeature", 101) }
    }
}
