package ru.medovik.hack2026.api.dto.request.risk

import com.fasterxml.jackson.annotation.JsonProperty

enum class TransactionType {
    @JsonProperty("order")
    ORDER,

    @JsonProperty("prolong")
    PROLONG,
}
