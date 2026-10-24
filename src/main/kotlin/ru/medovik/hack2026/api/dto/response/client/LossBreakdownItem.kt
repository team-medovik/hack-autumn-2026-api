package ru.medovik.hack2026.api.dto.response.client

import java.math.BigDecimal

data class LossBreakdownItem(
    val serviceId: String,
    val serviceName: String,
    val monthlyPrice: BigDecimal,
    val risk: Double,
    val loss: BigDecimal,
)
