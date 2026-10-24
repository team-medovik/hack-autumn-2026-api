package ru.medovik.hack2026.api.dto.request.risk

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Выгрузка биллинга: клиенты, услуги и транзакции. Принимается и вложенный вариант clients[].services.")
data class RiskSnapshot(
    @field:Schema(description = "Клиенты биллинга; минимум один")
    val accounts: List<Account>,
    @field:Schema(description = "Все услуги клиентов")
    val services: List<Service>,
    @field:Schema(description = "Транзакции; можно передать пустой массив")
    val transactions: List<Transaction> = emptyList(),
)
