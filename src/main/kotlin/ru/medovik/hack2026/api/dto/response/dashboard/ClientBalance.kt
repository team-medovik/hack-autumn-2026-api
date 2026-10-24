package ru.medovik.hack2026.api.dto.response.dashboard

import java.math.BigDecimal

data class ClientBalance(
    val available: BigDecimal,
    val required: BigDecimal,
)
