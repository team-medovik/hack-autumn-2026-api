package ru.medovik.hack2026.api.dto.response.risk

import java.time.Instant

data class RenewalCoverage(
    val serviceId: Long,
    val renewalAt: Instant,
    val price: Long,
    val fundsBefore: Long,
    val covered: Boolean,
    val fundsAfter: Long,
)
