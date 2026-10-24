package ru.medovik.hack2026.api.dto.request.risk

import com.fasterxml.jackson.annotation.JsonProperty

enum class PaymentTerm {
    @JsonProperty("hour")
    HOUR,

    @JsonProperty("day")
    DAY,

    @JsonProperty("week")
    WEEK,

    @JsonProperty("month")
    MONTH,

    @JsonProperty("quarter_year")
    QUARTER_YEAR,

    @JsonProperty("half_year")
    HALF_YEAR,

    @JsonProperty("year")
    YEAR,
}
