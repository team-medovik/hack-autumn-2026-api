package ru.medovik.hack2026.api.dto.settings

import io.swagger.v3.oas.annotations.media.Schema
import com.fasterxml.jackson.annotation.JsonAnyGetter
import com.fasterxml.jackson.annotation.JsonAnySetter
import com.fasterxml.jackson.annotation.JsonIgnore

@Schema(description = "Веса факторов риска: каждое значение от 0 до 100")
data class FactorWeights(
    @field:Schema(description = "Ожидание оплаты", minimum = "0", maximum = "100", example = "70")
    val awaitingPayment: Int,
    @field:Schema(description = "Истечение без автопродления", minimum = "0", maximum = "100", example = "50")
    val expiringWithoutAutoRenewal: Int,
    @field:Schema(description = "Недостаточно средств", minimum = "0", maximum = "100", example = "40")
    val insufficientFunds: Int,
    @field:Schema(description = "Ожидание оплаты без средств", minimum = "0", maximum = "100", example = "30")
    val awaitingPaymentWithoutFunds: Int,
    @field:Schema(description = "Отключение клиентом", minimum = "0", maximum = "100", example = "15")
    val disabledByClient: Int,
    @field:Schema(description = "Бонусов больше денег", minimum = "0", maximum = "100", example = "10")
    val bonusesExceedMoney: Int,
    @get:JsonIgnore
    val additionalWeights: MutableMap<String, Int> = linkedMapOf(),
) {
    // Additional, registered features are serialized alongside fixed weight keys.
    @JsonAnyGetter
    fun additionalProperties(): Map<String, Int> = additionalWeights

    @JsonAnySetter
    fun putAdditionalWeight(name: String, weight: Int) {
        require(name !in BUILTIN_KEYS) { "Duplicate built-in weight: $name" }
        require(weight in 0..100) { "Weight $name must be in 0..100" }
        additionalWeights[name] = weight
    }

    companion object {
        val BUILTIN_KEYS = setOf(
            "awaitingPayment", "expiringWithoutAutoRenewal", "insufficientFunds",
            "awaitingPaymentWithoutFunds", "disabledByClient", "bonusesExceedMoney",
        )
    }
}
