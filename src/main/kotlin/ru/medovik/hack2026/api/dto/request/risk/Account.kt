package ru.medovik.hack2026.api.dto.request.risk

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Клиент биллинга. Денежные значения указаны в евроцентах.")
data class Account(
    @field:Schema(description = "Уникальный ID клиента", example = "1")
    val id: Long,
    @field:Schema(description = "Email клиента", example = "client@example.test")
    val email: String,
    @field:Schema(description = "Основной баланс в евроцентах", minimum = "0", example = "10000")
    val balance: Long,
    @field:Schema(description = "Выводимый баланс в евроцентах", minimum = "0", example = "0")
    val withdrawBalance: Long,
    @field:Schema(description = "Сумма пополнений в евроцентах", minimum = "0", example = "10000")
    val totalReplenished: Long,
    @field:Schema(description = "Бонусный баланс в евроцентах", minimum = "0", example = "0")
    val bonusBalance: Long,
    @field:Schema(description = "Статус бонусов", example = "locked")
    val bonusState: BonusState,
) {
    init {
        require(listOf(balance, withdrawBalance, totalReplenished, bonusBalance).all { it >= 0 }) {
            "Account $id: amounts must be non-negative eurocents"
        }
    }
}
