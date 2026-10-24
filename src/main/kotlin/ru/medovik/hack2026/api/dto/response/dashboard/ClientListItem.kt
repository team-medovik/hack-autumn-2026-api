package ru.medovik.hack2026.api.dto.response.dashboard

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Строка сводной таблицы клиентов.")
data class ClientListItem(
    @field:Schema(description = "ID клиента в биллинге.", example = "1321245")
    val id: String,
    @field:Schema(description = "Email клиента.", example = "client5@mail.ru")
    val email: String,
    @field:Schema(description = "Оценка риска от 0 до 100. null, если оценка не рассчитывалась.", example = "94.2", minimum = "0", maximum = "100", nullable = true)
    val score: Double?,
    @field:Schema(description = "Уровень риска.", allowableValues = ["high", "medium", "low", "none"], example = "high")
    val level: String,
    @field:Schema(description = "Главная причина риска. null, если причины нет.", example = "Ожидает оплаты", nullable = true)
    val reason: String?,
    @field:Schema(description = "Текущий баланс клиента в евро.", example = "6.48")
    val balance: BigDecimal,
    @field:Schema(description = "Сумма всех пополнений клиента в евро.", example = "148.00")
    val topUp: BigDecimal,
    @field:Schema(description = "Ожидаемая ежемесячная потеря, если клиент уйдёт, в евро.", example = "6.77")
    val monthlyLoss: BigDecimal,
)
