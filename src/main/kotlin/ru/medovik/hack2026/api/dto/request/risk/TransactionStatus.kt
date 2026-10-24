package ru.medovik.hack2026.api.dto.request.risk

import com.fasterxml.jackson.annotation.JsonProperty

enum class TransactionStatus {
    @JsonProperty("performed")
    PERFORMED,

    @JsonProperty("not_performed")
    NOT_PERFORMED,
}
