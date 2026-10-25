package ru.medovik.hack2026.api.dto.response.client

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import com.fasterxml.jackson.annotation.JsonInclude

@Schema(description = "Услуга клиента с оценкой риска и ближайшим событием")
data class ClientService(
    @field:Schema(description = "ID услуги", example = "12")
    val id: String,
    @field:Schema(description = "Название услуги")
    val name: String,
    @field:Schema(description = "Score услуги или null для неактивной услуги", nullable = true)
    val score: Double?,
    @field:Schema(description = "Уровень риска", allowableValues = ["high", "medium", "low", "none"])
    val level: String,
    @field:Schema(description = "Статус для интерфейса", example = "active")
    val status: String,
    @field:Schema(description = "Стоимость в месяц в евро или null для неактивной услуги", nullable = true)
    val monthlyPrice: BigDecimal?,
    @field:Schema(description = "Следующее событие или null", nullable = true)
    val event: ClientServiceEvent?,
    @field:Schema(description = "Автопродление или null для неактивной услуги", nullable = true)
    val autoRenew: Boolean?,
    @get:JsonInclude(JsonInclude.Include.ALWAYS)
    @field:Schema(description = "Вероятность отсутствия продления или оплаченной замены к сроку + 5 суток; null, если прогноза ML нет. Не score/100.",
        nullable = true, minimum = "0", maximum = "1", example = "0.75")
    val lossProbability: Double? = null,
)
