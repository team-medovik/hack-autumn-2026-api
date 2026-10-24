package ru.medovik.hack2026.api.dto.request.risk

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Состояние бонусов: locked или unlocked")
enum class BonusState {
    @JsonProperty("locked")
    LOCKED,

    @JsonProperty("unlocked")
    UNLOCKED,
}
