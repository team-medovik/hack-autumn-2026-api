package ru.medovik.hack2026.api.dto.response.client

import java.math.BigDecimal

data class ClientCardBalance(
    val available: BigDecimal,
    val required: BigDecimal?,
    val monthlyLoss: BigDecimal,
)
