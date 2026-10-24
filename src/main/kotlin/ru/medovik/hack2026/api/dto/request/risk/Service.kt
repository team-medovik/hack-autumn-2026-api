package ru.medovik.hack2026.api.dto.request.risk

import java.time.Instant

data class Service(
    val id: Long,
    val name: String,
    val ownerId: Long,
    val paymentTerm: PaymentTerm,
    val autoProlong: Boolean,
    val createdAt: Instant,
    val expiresAt: Instant,
    val status: ServiceStatus,
    val price: Long,
) {
    init {
        require(price >= 0) { "Service $id: price must be non-negative eurocents" }
        require(!expiresAt.isBefore(createdAt)) { "Service $id: expiresAt precedes createdAt" }
    }
}
