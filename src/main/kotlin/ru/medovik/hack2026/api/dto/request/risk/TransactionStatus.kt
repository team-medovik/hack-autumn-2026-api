package ru.medovik.hack2026.api.dto.request.risk

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Статус транзакции: performed или not_performed")
enum class TransactionStatus {
    @JsonProperty("performed")
    PERFORMED,

    @JsonProperty("not_performed")
    NOT_PERFORMED,
}
