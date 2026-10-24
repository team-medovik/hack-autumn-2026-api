package ru.medovik.hack2026.api.contract

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import ru.medovik.hack2026.api.controller.contract.ContractDocumentationController
import ru.medovik.hack2026.api.controller.contract.ContractModelSettingsController
import ru.medovik.hack2026.api.exception.InvalidModelSettingsException
import ru.medovik.hack2026.api.service.settings.ModelSettingsService
import org.mockito.Mockito
import tools.jackson.databind.json.JsonMapper

/**
 * Does not require PostgreSQL. Complements the database-backed smoke/integration tests.
 * Fail on accidental divergence between the checked-in contract and exposed routes.
 */
class OpenApiContractGuardTest {
    private val document: Map<String, Any?> by lazy {
        javaClass.classLoader.getResourceAsStream("openapi/unified.yaml")!!.use {
            Yaml(SafeConstructor(LoaderOptions().apply { maxAliasesForCollections = 200 }))
                .load<Map<String, Any?>>(it)
        }
    }

    @Test
    fun `all operation IDs and referenced schemas resolve`() {
        @Suppress("UNCHECKED_CAST")
        val paths = document["paths"] as Map<String, Map<String, Any?>>
        @Suppress("UNCHECKED_CAST")
        val schemas = ((document["components"] as Map<String, Any?>)["schemas"] as Map<String, Any?>)
        val operationIds = mutableSetOf<String>()
        val methods = setOf("get", "post", "put", "patch", "delete")
        for ((path, pathItem) in paths) {
            for ((verb, value) in pathItem) {
                if (verb !in methods) continue
                @Suppress("UNCHECKED_CAST")
                val operation = value as Map<String, Any?>
                val operationId = operation["operationId"] as? String
                assertNotNull(operationId, "$verb $path is missing operationId")
                assertTrue(operationIds.add(operationId), "Duplicated operationId=$operationId")
                val responses = operation["responses"] as? Map<*, *>
                assertTrue(!responses.isNullOrEmpty(), "$verb $path is missing responses")
            }
        }
        assertEquals(23, operationIds.size)
        fun scan(value: Any?) {
            when (value) {
                is Map<*, *> -> {
                    val ref = value["\$ref"] as? String
                    if (ref != null && ref.startsWith("#/components/schemas/")) {
                        assertTrue(schemas.containsKey(ref.substringAfterLast('/')), "Unresolved $ref")
                    }
                    value.values.forEach(::scan)
                }
                is Iterable<*> -> value.forEach(::scan)
            }
        }
        scan(document)
    }

    @Test
    fun `standalone OpenAPI endpoint exports the exact bundled specification`() {
        val served = ContractDocumentationController().contract()
        assertEquals(document, served)
        assertEquals("3.1.0", served["openapi"])
    }

    @Test
    fun `PATCH rejects unknown properties, empty groups and invalid JSON types`() {
        val controller = ContractModelSettingsController(
            service = Mockito.mock(ModelSettingsService::class.java),
            jsonMapper = JsonMapper.builder().build(),
        )
        val auth = Mockito.mock(org.springframework.security.core.Authentication::class.java)
        val invalid = listOf<Map<String, Any?>>(
            mapOf("version" to 1, "changes" to mapOf("factorWeights" to mapOf("typo" to 50))),
            mapOf("version" to 1, "changes" to mapOf("forecast" to emptyMap<String, Any?>())),
            mapOf("version" to 1, "changes" to emptyMap<String, Any?>()),
            mapOf("version" to 0, "changes" to mapOf("forecast" to mapOf("horizonDays" to 5))),
            mapOf("version" to 1, "changes" to mapOf("forecast" to mapOf("horizonDays" to "5"))),
            mapOf("version" to 1, "changes" to mapOf("finance" to mapOf("bonusesAsMoney" to "true"))),
            mapOf("version" to 1, "changes" to mapOf("finance" to mapOf("balanceLossFactor" to null))),
            mapOf("version" to 1, "changes" to mapOf("riskThresholds" to mapOf("high" to 75)), "unexpected" to true),
        )
        for (payload in invalid) {
            assertFailsWith<InvalidModelSettingsException>("Payload must fail: $payload") {
                controller.patch(payload, auth)
            }
        }
        Mockito.verifyNoInteractions(controller.javaClass.getDeclaredField("service").let {
            it.isAccessible = true
            it.get(controller)
        })
    }

    @Test
    fun `canonical contract disallows extra properties in patch request`() {
        @Suppress("UNCHECKED_CAST")
        val components = document["components"] as Map<String, Any?>
        @Suppress("UNCHECKED_CAST")
        val schemas = components["schemas"] as Map<String, Map<String, Any?>>
        for (name in listOf("PatchModelSettingsRequest", "ModelSettingsChanges", "PatchFactorWeights", "PatchRiskThresholds", "PatchFinanceSettings", "PatchForecastSettings")) {
            assertFalse(schemas[name]?.get("additionalProperties") != false, "$name must disallow extra fields")
        }
    }
}
