package ru.medovik.hack2026.api.dto.response.client

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Вклад услуги в ожидаемые ежемесячные потери")
data class LossBreakdownItem(
    @field:Schema(description = "ID услуги")
    val serviceId: String,
    @field:Schema(description = "Название услуги")
    val serviceName: String,
    @field:Schema(description = "Стоимость в месяц в евро")
    val monthlyPrice: BigDecimal,
    @field:Schema(description = "Нормализованная оценка риска от 0 до 1")
    val risk: Double,
    @field:Schema(description = "Ожидаемая потеря в месяц в евро")
    val loss: BigDecimal,
)
