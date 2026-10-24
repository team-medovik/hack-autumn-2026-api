package ru.medovik.hack2026.api.dto.response.dashboard

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(description = "Самый рискованный клиент с расшифровкой оценки и потребности в средствах.")
class TopRiskClient(
    id: String,
    email: String,
    score: Double?,
    level: String,
    reason: String,
    monthlyLoss: BigDecimal,
    lossChanged: Boolean,
    @field:Schema(description = "Положительные факторы оценки, упорядоченные по вкладу.")
    val factors: List<ScoreFactor>,
    @field:Schema(description = "Текущий доступный баланс и сумма, нужная для ближайших продлений.")
    val balance: ClientBalance,
) : RiskClient(
    id = id,
    email = email,
    score = score,
    level = level,
    reason = reason,
    monthlyLoss = monthlyLoss,
    lossChanged = lossChanged,
)
