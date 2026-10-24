package ru.medovik.hack2026.api.dto.response.client

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Карточка клиента из активного снимка")
data class ClientCard(
    @field:Schema(description = "ID клиента", example = "1")
    val id: String,
    @field:Schema(description = "Адрес клиента", example = "client@example.test")
    val email: String,
    @field:Schema(description = "Итоговый score; null, если оценить клиента нельзя", nullable = true)
    val score: Double?,
    @field:Schema(description = "Уровень риска", allowableValues = ["high", "medium", "low", "none"])
    val level: String,
    @field:Schema(description = "Основные факторы риска")
    val factors: List<ClientFactor>,
    @field:Schema(description = "Баланс и ожидаемые потери")
    val balance: ClientCardBalance,
    @field:Schema(description = "Услуги клиента")
    val services: List<ClientService>,
    @field:Schema(description = "Состав ожидаемых потерь")
    val lossBreakdown: LossBreakdown,
)
