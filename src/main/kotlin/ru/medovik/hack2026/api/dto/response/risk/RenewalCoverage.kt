package ru.medovik.hack2026.api.dto.response.risk

import java.time.Instant

data class RenewalCoverage(
    val serviceId: Long,
    val renewalAt: Instant,
    val price: Long,
    val fundsBefore: Long,
    val covered: Boolean,
    val fundsAfter: Long,
) {
    val shortfall: Long get() = (price - fundsBefore).coerceAtLeast(0L)
    val shortageStrength: Double get() = if (price == 0L) 0.0 else shortfall.toDouble() / price.toDouble()
}
