package ru.medovik.hack2026.api.dto.request.risk

import com.fasterxml.jackson.annotation.JsonProperty

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
