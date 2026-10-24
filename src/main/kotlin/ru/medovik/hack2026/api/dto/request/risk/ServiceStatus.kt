package ru.medovik.hack2026.api.dto.request.risk

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Статус услуги: active, blocked, deleted, suspended или prolong_wait")
enum class ServiceStatus(
    val isLive: Boolean,
) {
    @JsonProperty("active")
    ACTIVE(true),

    @JsonProperty("blocked")
    BLOCKED(false),

    @JsonProperty("deleted")
    DELETED(false),

    @JsonProperty("suspended")
    SUSPENDED(true),

    @JsonProperty("prolong_wait")
    PROLONG_WAIT(true),
}
