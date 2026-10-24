package ru.medovik.hack2026.api.dto.request.risk

import com.fasterxml.jackson.annotation.JsonProperty

enum class BonusState {
    @JsonProperty("locked")
    LOCKED,

    @JsonProperty("unlocked")
    UNLOCKED,
}
