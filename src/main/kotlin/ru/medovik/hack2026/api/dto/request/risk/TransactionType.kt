package ru.medovik.hack2026.api.dto.request.risk

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Тип транзакции: order или prolong")
enum class TransactionType {
    @JsonProperty("order")
    ORDER,

    @JsonProperty("prolong")
    PROLONG,
}
