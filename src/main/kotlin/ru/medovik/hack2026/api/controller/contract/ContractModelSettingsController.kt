package ru.medovik.hack2026.api.controller.contract

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.settings.ModelSettings
import ru.medovik.hack2026.api.dto.settings.PatchModelSettingsRequest
import ru.medovik.hack2026.api.exception.InvalidModelSettingsException
import ru.medovik.hack2026.api.service.settings.ModelSettingsService
import tools.jackson.databind.json.JsonMapper

@RestController
@Tag(name = "Модель")
@SecurityRequirement(name = "bearerAuth")
class ContractModelSettingsController(
    private val service: ModelSettingsService,
    private val jsonMapper: JsonMapper,
) {
    @GetMapping("/api/v1/model/settings")
    @Operation(operationId = "getRiskModelSettings", summary = "Получить действующие настройки модели")
    fun get(): ModelSettings = service.get()

    /**
     * Do not silently drop unknown JSON keys: the unified OpenAPI schema explicitly forbids
     * additionalProperties on the request and each patch group.
     */
    @PatchMapping("/api/v1/model/settings")
    @Operation(operationId = "patchRiskModelSettings", summary = "Частично обновить параметры модели и пересчитать")
    fun patch(
        @RequestBody payload: Map<String, Any?>,
        authentication: Authentication,
    ): ModelSettings {
        validatePatchPayload(payload)
        val request = try {
            jsonMapper.readValue(jsonMapper.writeValueAsString(payload), PatchModelSettingsRequest::class.java)
        } catch (_: Exception) {
            throw InvalidModelSettingsException("Не удалось разобрать изменения модели")
        }
        return service.patch(request = request, updatedBy = authentication.name)
    }

    private fun validatePatchPayload(payload: Map<String, Any?>) {
        requireKeys(payload, setOf("version", "changes"), "PATCH")
        val version = payload["version"]
        if ((version !is Int && version !is Long) || (version as Number).toLong() < 1L) {
            throw InvalidModelSettingsException("version должен быть целым положительным числом")
        }
        val changes = requireMap(payload["changes"], "changes")
        requireKeys(changes, setOf("factorWeights", "riskThresholds", "finance", "forecast"), "changes")
        if (changes.isEmpty()) throw InvalidModelSettingsException("changes не может быть пустым")
        for ((group, allowed) in GROUP_FIELDS) {
            if (!changes.containsKey(group)) continue
            val fields = requireMap(changes[group], group)
            requireKeys(fields, allowed, group)
            if (fields.isEmpty()) throw InvalidModelSettingsException("$group не может быть пустым")
            for ((name, value) in fields) {
                if (value == null) throw InvalidModelSettingsException("$group.$name не может быть null")
                if (name in BOOLEAN_FIELDS) {
                    if (value !is Boolean) throw InvalidModelSettingsException("$group.$name должен быть boolean")
                } else if (name == "balanceLossFactor") {
                    if (value !is Number || !value.toDouble().isFinite()) {
                        throw InvalidModelSettingsException("$group.$name должен быть конечным числом")
                    }
                } else if (value !is Int && value !is Long) {
                    throw InvalidModelSettingsException("$group.$name должен быть целым числом")
                }
            }
        }
    }

    private fun requireMap(value: Any?, field: String): Map<String, Any?> {
        if (value !is Map<*, *> || value.keys.any { it !is String }) {
            throw InvalidModelSettingsException("$field должен быть JSON-объектом")
        }
        @Suppress("UNCHECKED_CAST")
        return value as Map<String, Any?>
    }

    private fun requireKeys(payload: Map<String, Any?>, allowed: Set<String>, location: String) {
        val unknown = payload.keys - allowed
        if (unknown.isNotEmpty()) throw InvalidModelSettingsException(
            "$location: неизвестные поля ${unknown.sorted().joinToString()}"
        )
    }

    private companion object {
        val BOOLEAN_FIELDS = setOf("bonusesAsMoney", "referralBalanceAsMoney")
        val GROUP_FIELDS = mapOf(
            "factorWeights" to setOf(
                "awaitingPayment", "expiringWithoutAutoRenewal", "insufficientFunds",
                "awaitingPaymentWithoutFunds", "disabledByClient", "bonusesExceedMoney",
            ),
            "riskThresholds" to setOf("medium", "high"),
            "finance" to setOf("bonusesAsMoney", "referralBalanceAsMoney", "balanceLossFactor"),
            "forecast" to setOf("horizonDays", "recalculationIntervalMinutes"),
        )
    }
}
