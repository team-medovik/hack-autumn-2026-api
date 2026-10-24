package ru.medovik.hack2026.api.dto.request.risk

import java.time.Instant

data class Transaction(
    val id: Long,
    val amount: Int,
    val status: TransactionStatus,
    val performedAt: Instant?,
    val createdAt: Instant,
    val type: TransactionType,
    val serviceId: Long,
) {
    init {
        require(amount in 0..1000) { "Transaction $id: amount must be in 0..1000" }
        require((status == TransactionStatus.PERFORMED) == (performedAt != null)) {
            "Transaction $id: status and performedAt disagree"
        }
        require(performedAt == null || !performedAt.isBefore(createdAt)) {
            "Transaction $id: performedAt precedes createdAt"
        }
    }
}
