package ru.medovik.hack2026.api.dto.request.risk

import io.swagger.v3.oas.annotations.media.Schema

import java.time.Instant

@Schema(description = "Услуга клиента в снимке биллинга")
data class Service(
    @field:Schema(description = "Уникальный ID услуги", example = "1")
    val id: Long,
    @field:Schema(description = "Название услуги", example = "hosting-1")
    val name: String,
    @field:Schema(description = "ID клиента-владельца", example = "1")
    val ownerId: Long,
    @field:Schema(description = "Платёжный период", example = "month")
    val paymentTerm: PaymentTerm,
    @field:Schema(description = "Автопродление включено", example = "true")
    val autoProlong: Boolean,
    @field:Schema(description = "Момент создания в UTC")
    val createdAt: Instant,
    @field:Schema(description = "Момент окончания оплаченного периода в UTC")
    val expiresAt: Instant,
    @field:Schema(description = "Статус услуги", example = "active")
    val status: ServiceStatus,
    @field:Schema(description = "Стоимость периода в евроцентах", minimum = "0", example = "1200")
    val price: Long,
) {
    init {
        require(price >= 0) { "Service $id: price must be non-negative eurocents" }
        require(!expiresAt.isBefore(createdAt)) { "Service $id: expiresAt precedes createdAt" }
    }
}
