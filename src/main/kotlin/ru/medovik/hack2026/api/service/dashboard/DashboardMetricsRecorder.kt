package ru.medovik.hack2026.api.service.dashboard

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MultiGauge
import io.micrometer.core.instrument.Tags
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import ru.medovik.hack2026.api.dto.response.dashboard.DashboardResponse
import java.time.ZoneOffset

/** Публикует показатели дашборда без идентификаторов клиентов и других неограниченных меток. */
@Component
class DashboardMetricsRecorder(registry: MeterRegistry) {
    companion object {
        const val METRIC_PREFIX = "churn."
    }

    private val customers = gauge(registry, "churn.customers", "Количество клиентов по уровням риска оттока")
    private val monthlyLoss = gauge(registry, "churn.expected.monthly.loss.eur", "Ожидаемые ежемесячные потери в евро")
    private val snapshotPresent = gauge(registry, "churn.snapshot.present", "Наличие снимка биллинга: 1 — есть, 0 — нет")
    private val snapshotTakenAt = gauge(registry, "churn.snapshot.taken.at.seconds", "Время формирования снимка биллинга в секундах Unix")
    private val bucketLoss = gauge(registry, "churn.risk.bucket.monthly.loss.eur", "Ожидаемые ежемесячные потери по уровням риска в евро")
    private val bucketChanged = gauge(registry, "churn.risk.bucket.changed", "Изменение потерь уровня риска относительно предыдущего снимка: 1 — да, 0 — нет")
    private val topScore = gauge(registry, "churn.top.risk.score", "Наибольшая оценка риска клиента")
    private val topLoss = gauge(registry, "churn.top.risk.monthly.loss.eur", "Ожидаемые ежемесячные потери по самому рискованному клиенту в евро")
    private val topLossChanged = gauge(registry, "churn.top.risk.loss.changed", "Изменение потерь по самому рискованному клиенту: 1 — да, 0 — нет")
    private val topBalanceAvailable = gauge(registry, "churn.top.risk.balance.available.eur", "Доступный баланс самого рискованного клиента в евро")
    private val topBalanceRequired = gauge(registry, "churn.top.risk.balance.required.eur", "Сумма для продления услуг самого рискованного клиента в евро")
    private val topFactorPoints = gauge(registry, "churn.top.risk.factor.points", "Вклад факторов в оценку риска самого рискованного клиента")
    private val queueTotal = gauge(registry, "churn.risk.queue.total", "Общее количество клиентов в последнем снимке биллинга")
    private val queueSize = gauge(registry, "churn.risk.queue.size", "Количество клиентов, показанных в очереди внимания")
    private val queueScore = gauge(registry, "churn.risk.queue.score", "Оценка риска клиента по месту в очереди")
    private val queueLoss = gauge(registry, "churn.risk.queue.monthly.loss.eur", "Ожидаемые ежемесячные потери по месту в очереди в евро")
    private val queueLossChanged = gauge(registry, "churn.risk.queue.loss.changed", "Изменение потерь по месту в очереди: 1 — да, 0 — нет")
    private val reasonClients = gauge(registry, "churn.loss.reason.clients", "Количество клиентов по основной причине потерь")
    private val reasonLoss = gauge(registry, "churn.loss.reason.monthly.loss.eur", "Ожидаемые ежемесячные потери по основной причине в евро")
    private val atRiskServices = gauge(registry, "churn.at.risk.services", "Количество услуг с риском оттока сейчас")
    private val weeklyServices = gauge(registry, "churn.weekly.services", "Удаления или прогноз непродлений услуг по неделям; week_offset отсчитывается от текущей недели")
    private val weekStart = gauge(registry, "churn.week.start.seconds", "Начало недели оттока в секундах Unix; week_offset отсчитывается от текущей недели")
    private val lastUpdate = gauge(registry, "churn.dashboard.updated.at.seconds", "Время последнего успешного обновления метрик дашборда в секундах Unix")

    @EventListener
    fun record(dashboard: DashboardResponse) {
        customers.replace(dashboard.riskBuckets.map { row(Tags.of("level", it.level), it.clients) })
        monthlyLoss.replace(listOf(row(Tags.empty(), dashboard.monthlyLoss.toDouble())))
        snapshotPresent.replace(listOf(row(Tags.empty(), if (dashboard.snapshot == null) 0 else 1)))
        snapshotTakenAt.replace(dashboard.snapshot?.let { listOf(row(Tags.empty(), it.takenAt.toEpochSecond())) }.orEmpty())
        bucketLoss.replace(dashboard.riskBuckets.mapNotNull { bucket ->
            bucket.monthlyLoss?.let { row(Tags.of("level", bucket.level), it.toDouble()) }
        })
        bucketChanged.replace(dashboard.riskBuckets.map { row(Tags.of("level", it.level), if (it.changed) 1 else 0) })

        val top = dashboard.topRisk
        topScore.replace(top?.score?.let { listOf(row(Tags.empty(), it)) }.orEmpty())
        topLoss.replace(top?.let { listOf(row(Tags.empty(), it.monthlyLoss.toDouble())) }.orEmpty())
        topLossChanged.replace(top?.let { listOf(row(Tags.empty(), if (it.lossChanged) 1 else 0)) }.orEmpty())
        topBalanceAvailable.replace(top?.let { listOf(row(Tags.empty(), it.balance.available.toDouble())) }.orEmpty())
        topBalanceRequired.replace(top?.let { listOf(row(Tags.empty(), it.balance.required.toDouble())) }.orEmpty())
        topFactorPoints.replace(top?.factors.orEmpty().map { row(Tags.of("factor", it.id), it.points) })

        queueTotal.replace(listOf(row(Tags.empty(), dashboard.riskQueue.total)))
        queueSize.replace(listOf(row(Tags.empty(), dashboard.riskQueue.items.size)))
        val queue = dashboard.riskQueue.items.mapIndexed { index, item ->
            Tags.of("rank", (index + 1).toString(), "level", item.level) to item
        }
        queueScore.replace(queue.mapNotNull { (tags, item) -> item.score?.let { row(tags, it) } })
        queueLoss.replace(queue.map { (tags, item) -> row(tags, item.monthlyLoss.toDouble()) })
        queueLossChanged.replace(queue.map { (tags, item) -> row(tags, if (item.lossChanged) 1 else 0) })

        reasonClients.replace(dashboard.lossReasons.map { row(Tags.of("reason", it.id, "level", it.level), it.clients) })
        reasonLoss.replace(dashboard.lossReasons.map { row(Tags.of("reason", it.id, "level", it.level), it.monthlyLoss.toDouble()) })
        atRiskServices.replace(listOf(row(Tags.empty(), dashboard.churn.atRiskServices)))
        val weeks = dashboard.churn.weeks.groupBy { it.kind }.flatMap { (kind, items) ->
            items.mapIndexed { index, week ->
                val offset = if (kind == "fact") index - items.size else index
                Tags.of("kind", kind, "week_offset", offset.toString()) to week
            }
        }
        weeklyServices.replace(weeks.map { (tags, week) -> row(tags, week.services) })
        weekStart.replace(weeks.map { (tags, week) -> row(tags, week.weekStart.atStartOfDay(ZoneOffset.UTC).toEpochSecond()) })
        lastUpdate.replace(listOf(row(Tags.empty(), System.currentTimeMillis() / 1000.0)))
    }

    private fun gauge(registry: MeterRegistry, name: String, description: String): MultiGauge =
        MultiGauge.builder(name).description(description).register(registry)

    private fun row(tags: Tags, value: Number): MultiGauge.Row<Number> = MultiGauge.Row.of(tags, value)

    private fun MultiGauge.replace(rows: List<MultiGauge.Row<Number>>) = register(rows, true)
}
