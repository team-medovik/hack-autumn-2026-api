package ru.medovik.hack2026.api.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.constructor.SafeConstructor
import ru.medovik.hack2026.api.dto.settings.ModelSettingsChanges
import ru.medovik.hack2026.api.dto.settings.PatchFactorWeights
import ru.medovik.hack2026.api.dto.settings.PatchRiskThresholds
import ru.medovik.hack2026.api.dto.settings.FactorWeights
import ru.medovik.hack2026.api.dto.settings.RiskThresholds
import ru.medovik.hack2026.api.dto.settings.FinanceSettings
import ru.medovik.hack2026.api.dto.settings.ForecastSettings
import ru.medovik.hack2026.api.dto.settings.ModelSettingsValues
import ru.medovik.hack2026.api.exception.InvalidModelSettingsException

class UnifiedContractTest {
    @Test
    fun `all mandatory case endpoints are in the bundled canonical OpenAPI`() {
        val spec = javaClass.classLoader.getResourceAsStream("openapi/unified.yaml")!!.use {
            Yaml(SafeConstructor(LoaderOptions().apply { maxAliasesForCollections = 200 }))
                .load<Map<String, Any>>(it)
        }
        assertEquals("3.1.0", spec["openapi"])
        @Suppress("UNCHECKED_CAST")
        val paths = spec["paths"] as Map<String, Map<String, Any>>
        val required = mapOf(
            "/api/v1/auth/login" to "post",
            "/api/v1/auth/me" to "get",
            "/api/v1/auth/logout" to "post",
            "/api/v1/customers" to "get",
            "/api/v1/customers/{id}" to "get",
            "/api/v1/analytics/summary" to "get",
            "/api/v1/model/settings" to "patch",
            "/api/v1/source/snapshot" to "put",
            "/metrics" to "get",
            "/api/v1/docs" to "get",
            "/api/v1/openapi" to "get",
        )
        for ((path, verb) in required) {
            assertTrue(paths[path]?.containsKey(verb) == true, "Missing $verb $path")
        }
        val exported = paths["/api/v1/openapi"]!!["get"] as Map<*, *>
        val responses = exported["responses"] as Map<*, *>
        val ok = responses["200"] as Map<*, *>
        val contents = ok["content"] as Map<*, *>
        assertTrue(contents.containsKey("application/json"), "OpenAPI endpoint must return JSON")
    }

    @Test
    fun `partial PATCH preserves unspecified values`() {
        val original = ModelSettingsValues(
            factorWeights = FactorWeights(70, 50, 40, 30, 15, 10),
            riskThresholds = RiskThresholds(40, 70),
            finance = FinanceSettings(true, false, 0.5),
            forecast = ForecastSettings(5, 5),
        )
        val next = ModelSettingsChanges(
            factorWeights = PatchFactorWeights(awaitingPayment = 72),
            riskThresholds = PatchRiskThresholds(high = 75),
        ).mergeInto(original)
        assertEquals(72, next.factorWeights.awaitingPayment)
        assertEquals(50, next.factorWeights.expiringWithoutAutoRenewal)
        assertEquals(40, next.riskThresholds.medium)
        assertEquals(75, next.riskThresholds.high)
        assertEquals(original.finance, next.finance)
        assertEquals(original.forecast, next.forecast)
    }

    @Test
    fun `empty PATCH does not silently succeed`() {
        val original = ModelSettingsValues(
            factorWeights = FactorWeights(70, 50, 40, 30, 15, 10),
            riskThresholds = RiskThresholds(40, 70),
            finance = FinanceSettings(true, false, 0.5),
            forecast = ForecastSettings(5, 5),
        )
        assertFailsWith<InvalidModelSettingsException> {
            ModelSettingsChanges().mergeInto(original)
        }
    }
}
