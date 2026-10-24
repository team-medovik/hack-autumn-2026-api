package ru.medovik.hack2026.api.dto.request.risk

import io.swagger.v3.oas.annotations.media.Schema

import java.time.Instant

@Schema(description = "Транзакция услуги в снимке биллинга")
data class Transaction(
    @field:Schema(description = "Уникальный ID транзакции", example = "1")
    val id: Long,
    @field:Schema(description = "Сумма по исходному формату", minimum = "0", example = "100")
    val amount: Int,
    @field:Schema(description = "Статус транзакции", example = "performed")
    val status: TransactionStatus,
    @field:Schema(description = "Время исполнения в UTC; null для not_performed", nullable = true)
    val performedAt: Instant?,
    @field:Schema(description = "Время создания в UTC")
    val createdAt: Instant,
    @field:Schema(description = "Тип операции", example = "order")
    val type: TransactionType,
    @field:Schema(description = "ID услуги", example = "1")
    val serviceId: Long,
) {
    init {
        require(amount >= 0) { "Transaction $id: amount must be non-negative eurocents" }
        require((status == TransactionStatus.PERFORMED) == (performedAt != null)) {
            "Transaction $id: status and performedAt disagree"
        }
        require(performedAt == null || !performedAt.isBefore(createdAt)) {
            "Transaction $id: performedAt precedes createdAt"
        }
    }
}
