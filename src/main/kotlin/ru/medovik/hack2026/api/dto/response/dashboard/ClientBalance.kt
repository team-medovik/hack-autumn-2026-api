package ru.medovik.hack2026.api.dto.response.dashboard

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Средства клиента и сумма ближайших продлений.")
data class ClientBalance(
    @field:Schema(description = "Текущий доступный баланс клиента.", example = "0.00")
    val available: BigDecimal,
    @field:Schema(description = "Сумма, нужная на продления в ближайшие дни.", example = "28.80")
    val required: BigDecimal,
)
