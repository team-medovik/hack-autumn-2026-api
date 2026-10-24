package ru.medovik.hack2026.api.dto.response.dashboard

import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(
    description = "Сводка главного экрана по последнему снимку биллинга. При отсутствии снимка содержит пустое состояние."
)
data class DashboardResponse(
    @field:Schema(description = "Снимок, по которому построена сводка. null, если снимки ещё не загружали.", nullable = true)
    val snapshot: SnapshotInfo?,
    @field:Schema(description = "Сумма ежемесячных потерь по клиентам с риском.", example = "1296.90")
    val monthlyLoss: BigDecimal,
    @field:Schema(description = "Ровно четыре агрегата: high, medium, low и none.")
    val riskBuckets: List<RiskBucket>,
    @field:Schema(description = "Клиент с наибольшей оценкой риска. null, если выделять некого.", nullable = true)
    val topRisk: TopRiskClient?,
    @field:Schema(description = "Первые пять клиентов, требующих внимания.")
    val riskQueue: RiskQueue,
    @field:Schema(description = "Причины ожидаемых потерь, отсортированные по убыванию суммы.")
    val lossReasons: List<LossReason>,
    @field:Schema(description = "Факт удалений и прогноз непродлений услуг по неделям.")
    val churn: ChurnForecast,
)
