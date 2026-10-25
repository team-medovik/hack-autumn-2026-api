package ru.medovik.hack2026.api.service.dashboard

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Transactional
import org.slf4j.LoggerFactory
import ru.medovik.hack2026.api.dto.request.risk.Account
import ru.medovik.hack2026.api.dto.request.risk.RiskSettings
import ru.medovik.hack2026.api.dto.request.risk.PaymentTerm
import ru.medovik.hack2026.api.dto.request.risk.RiskSnapshot
import ru.medovik.hack2026.api.dto.request.risk.ServiceStatus
import ru.medovik.hack2026.api.dto.response.dashboard.ChurnForecast
import ru.medovik.hack2026.api.dto.response.dashboard.ChurnWeek
import ru.medovik.hack2026.api.dto.response.dashboard.ClientBalance
import ru.medovik.hack2026.api.dto.response.dashboard.ClientListItem
import ru.medovik.hack2026.api.dto.response.dashboard.ClientsResponse
import ru.medovik.hack2026.api.dto.response.dashboard.DashboardResponse
import ru.medovik.hack2026.api.dto.response.dashboard.LevelCounts
import ru.medovik.hack2026.api.dto.response.dashboard.LossReason
import ru.medovik.hack2026.api.dto.response.dashboard.RiskBucket
import ru.medovik.hack2026.api.dto.response.dashboard.RiskClient
import ru.medovik.hack2026.api.dto.response.dashboard.RiskQueue
import ru.medovik.hack2026.api.dto.response.dashboard.ScoreFactor
import ru.medovik.hack2026.api.dto.response.dashboard.SnapshotInfo
import ru.medovik.hack2026.api.dto.response.dashboard.TopRiskClient
import ru.medovik.hack2026.api.dto.response.client.ClientCard
import ru.medovik.hack2026.api.dto.response.client.ClientCardBalance
import ru.medovik.hack2026.api.dto.response.client.ClientFactor
import ru.medovik.hack2026.api.dto.response.client.ClientPosition
import ru.medovik.hack2026.api.dto.response.client.ClientService
import ru.medovik.hack2026.api.dto.response.client.ClientServiceEvent
import ru.medovik.hack2026.api.dto.response.client.LossBreakdown
import ru.medovik.hack2026.api.dto.response.client.LossBreakdownItem
import ru.medovik.hack2026.api.dto.response.risk.CustomerRiskResult
import ru.medovik.hack2026.api.dto.response.risk.FeatureContribution
import ru.medovik.hack2026.api.dto.response.risk.RiskResult
import ru.medovik.hack2026.api.dto.response.risk.ServiceRiskResult
import ru.medovik.hack2026.api.repository.dashboard.BillingSnapshotRepository
import ru.medovik.hack2026.api.repository.dashboard.StoredBillingSnapshot
import ru.medovik.hack2026.api.service.risk.RiskEngine
import ru.medovik.hack2026.api.service.ml.MlAssessmentService
import ru.medovik.hack2026.api.service.settings.ModelSettingsService
import ru.medovik.hack2026.api.service.settings.CalculationDateService
import tools.jackson.databind.json.JsonMapper
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.Duration
import java.time.temporal.TemporalAdjusters

@Service
@Transactional(readOnly = true)
class DashboardService(
    private val snapshots: BillingSnapshotRepository,
    private val jsonMapper: JsonMapper,
    private val modelSettings: ModelSettingsService,
    private val calculationDate: CalculationDateService,
    private val mlAssessment: MlAssessmentService,
) {
    private val riskEngine = RiskEngine()
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    fun getDashboard(): DashboardResponse {
        val history = snapshots.findDashboardHistory()
        if (history.isEmpty()) {
            logger.debug("Dashboard requested without billing snapshots")
            return emptyDashboard()
        }

        val current = load(history.first())
        val clients = project(current)
        val previous = history.getOrNull(1)?.let { project(load(it)) }
        val previousById = previous?.associateBy { it.account.id }.orEmpty()
        val buckets = LEVELS.map { level ->
            val members = clients.filter { it.level == level }
            val loss = if (level == "none") null else members.sumMoney { it.monthlyLoss }
            val oldLoss = previous?.filter { it.level == level }?.sumMoney { it.monthlyLoss }
            RiskBucket(
                level = level,
                clients = members.size,
                monthlyLoss = loss,
                changed = oldLoss != null && oldLoss != loss,
            )
        }
        val ranked = clients.filter { (it.score ?: 0.0) > 0 }
            .sortedWith(compareByDescending<ClientProjection> { it.score }.thenBy { it.account.id })
        val top = ranked.firstOrNull()?.let { client ->
            TopRiskClient(
                id = client.account.id.toString(),
                email = client.account.email,
                score = client.score,
                level = client.level,
                reason = client.reason ?: "Риск не определён",
                monthlyLoss = client.monthlyLoss,
                lossChanged = lossChanged(client = client, old = previousById),
                factors = client.factors,
                balance = ClientBalance(
                    available = euros(client.account.balance),
                    required = euros(client.risk.funding.requiredFunds),
                ),
            )
        }
        val reasons = ranked.filter { it.reasonId != null }
            .groupBy { it.reasonId }
            .map { (id, members) ->
                val reasonId = requireNotNull(id)
                LossReason(
                    id = reasonId,
                    label = reasonLabel(reasonId),
                    level = LEVELS.first { level -> members.any { it.level == level } },
                    clients = members.size,
                    monthlyLoss = members.sumMoney { it.monthlyLoss },
                )
            }
            .sortedWith(compareByDescending<LossReason> { it.monthlyLoss }.thenBy { it.id })

        val response = DashboardResponse(
            snapshot = SnapshotInfo(id = current.stored.id, takenAt = current.stored.takenAt),
            monthlyLoss = clients.sumMoney { it.monthlyLoss },
            riskBuckets = buckets,
            topRisk = top,
            riskQueue = RiskQueue(
                total = clients.size,
                items = ranked.take(5).map { client ->
                    RiskClient(
                        id = client.account.id.toString(),
                        email = client.account.email,
                        score = client.score,
                        level = client.level,
                        reason = client.reason ?: "Риск не определён",
                        monthlyLoss = client.monthlyLoss,
                        lossChanged = lossChanged(client = client, old = previousById),
                    )
                },
            ),
            lossReasons = reasons,
            churn = churn(current = current, history = history),
        )
        logger.debug(
            "Dashboard built: snapshotId={}, historySize={}, clients={}, riskQueue={}, lossReasons={}, churnWeeks={}",
            current.stored.id,
            history.size,
            clients.size,
            response.riskQueue.items.size,
            response.lossReasons.size,
            response.churn.weeks.size,
        )
        return response
    }

    fun listClients(
        level: String?,
        search: String?,
        sort: String?,
        order: String?,
        page: String?,
        pageSize: String?,
    ): ClientsResponse {
        val normalizedLevel = level?.lowercase()
        require(normalizedLevel == null || normalizedLevel in LEVELS) { "Неизвестный уровень риска" }
        val sortField = sort ?: "score"
        require(sortField in SORT_FIELDS) { "Неизвестное поле сортировки" }
        val direction = order ?: "desc"
        require(direction == "asc" || direction == "desc") { "Порядок должен быть asc или desc" }
        val requestedPage = page?.toIntOrNull() ?: if (page == null) 1 else 0
        val size = pageSize?.toIntOrNull() ?: if (pageSize == null) 12 else 0
        require(requestedPage >= 1) { "page должен быть положительным числом" }
        require(size in 1..100) { "pageSize должен быть от 1 до 100" }

        val stored = snapshots.findLatest()
        if (stored == null) {
            logger.debug(
                "Client list requested without billing snapshots: level={}, searchPresent={}, sort={}, order={}, page={}, pageSize={}",
                normalizedLevel,
                search.isNullOrBlank().not(),
                sortField,
                direction,
                requestedPage,
                size,
            )
            return ClientsResponse(
                items = emptyList(),
                page = 1,
                pageSize = size,
                total = 0,
                levelCounts = counts(emptyList()),
            )
        }
        val clients = project(load(stored))
        val query = search?.trim()?.takeIf { it.isNotEmpty() }
        val sorted = filterAndSort(
            clients = clients,
            level = level,
            search = search,
            sort = sort,
            order = order,
        )
        val lastPage = if (sorted.isEmpty()) 1 else (sorted.size - 1) / size + 1
        val actualPage = requestedPage.coerceAtMost(lastPage)
        val first = (actualPage - 1) * size
        val response = ClientsResponse(
            items = sorted.drop(first).take(size).map { client ->
                ClientListItem(
                    id = client.account.id.toString(),
                    email = client.account.email,
                    score = client.score,
                    level = client.level,
                    reason = client.reason,
                    balance = euros(client.account.balance),
                    topUp = euros(client.account.totalReplenished),
                    monthlyLoss = client.monthlyLoss,
                )
            },
            page = actualPage,
            pageSize = size,
            total = sorted.size,
            levelCounts = counts(clients),
        )
        logger.debug(
            "Client list built: snapshotId={}, level={}, searchPresent={}, sort={}, order={}, requestedPage={}, page={}, pageSize={}, total={}",
            stored.id,
            normalizedLevel,
            query != null,
            sortField,
            direction,
            requestedPage,
            response.page,
            size,
            response.total,
        )
        return response
    }

    fun getClientCard(id: String): ClientCard {
        val stored = snapshots.findLatest() ?: throw NoSuchElementException("Клиент не найден")
        val snapshot = load(stored)
        val client = project(snapshot).firstOrNull { it.account.id.toString() == id }
            ?: throw NoSuchElementException("Клиент не найден")
        val sourceServices = snapshot.payload.services.filter { it.ownerId == client.account.id }
        val scored = snapshot.risk.services.associateBy { it.serviceId }
        val breakdown = lossItems(
            services = sourceServices,
            scored = scored,
            settings = snapshot.risk.settings,
            funding = client.risk.funding,
        )
        val leading = snapshot.risk.services.filter { it.ownerId == client.account.id && it.score != null }
            .maxWithOrNull(compareBy<ServiceRiskResult> { it.score }.thenBy { -it.serviceId })
        val positive = leading?.features.orEmpty().filter { it.contribution > 0 }
            .sortedWith(compareByDescending<FeatureContribution> { it.contribution }.thenBy { it.code })
        val rawScore = leading?.rawScore ?: 0.0
        val factorScale = if (rawScore > 100.0) 100.0 / rawScore else 1.0
        val factors = positive.mapIndexed { index, feature ->
            ClientFactor(
                id = reasonId(feature.code),
                label = feature.explanation + if (index == 0) leading?.serviceId?.let { snapshot.mlAnnotations[it] }?.let { "; $it" }.orEmpty() else "",
                points = feature.contribution * factorScale,
            )
        }
        val cardFactors = if (factors.isEmpty() && leading?.score != null && leading.score > 0 && snapshot.mlAnnotations.containsKey(leading.serviceId)) {
            listOf(ClientFactor("ml-adjustment", snapshot.mlAnnotations.getValue(leading.serviceId), leading.score))
        } else factors
        val services = sourceServices.map { service ->
            val risk = scored.getValue(service.id)
            val status = when (service.status) {
                ServiceStatus.PROLONG_WAIT -> "awaiting_payment"
                ServiceStatus.ACTIVE -> "active"
                ServiceStatus.SUSPENDED -> "disabled_by_client"
                ServiceStatus.DELETED -> "deleted"
                ServiceStatus.BLOCKED -> "blocked"
            }
            val event = when (service.status) {
                ServiceStatus.PROLONG_WAIT -> ClientServiceEvent(
                    type = "delete",
                    date = service.expiresAt.plus(Duration.ofDays(5)).atZone(ZoneOffset.UTC).toLocalDate(),
                )
                ServiceStatus.ACTIVE, ServiceStatus.SUSPENDED -> ClientServiceEvent(
                    type = if (service.autoProlong) "renew" else "expire",
                    date = service.expiresAt.atZone(ZoneOffset.UTC).toLocalDate(),
                )
                ServiceStatus.DELETED, ServiceStatus.BLOCKED -> null
            }
            ClientService(
                id = service.id.toString(),
                name = service.name,
                score = risk.score,
                level = risk.level.name.lowercase(),
                status = status,
                monthlyPrice = if (service.status.isLive) {
                    monthlyPrice(cents = service.price, term = service.paymentTerm)
                } else null,
                event = event,
                autoRenew = if (service.status.isLive) service.autoProlong else null,
            )
        }
        val funding = client.risk.funding
        val card = ClientCard(
            id = id,
            email = client.account.email,
            score = client.score,
            level = client.level,
            factors = cardFactors,
            balance = ClientCardBalance(
                available = euros(funding.availableFunds),
                required = if (client.score == null || funding.requiredFunds == 0L) null else euros(funding.requiredFunds),
                monthlyLoss = breakdown.fold(ZERO) { sum, item -> sum + item.loss },
                bonus = euros(client.account.bonusBalance),
                referral = euros(client.account.withdrawBalance),
            ),
            services = services,
            lossBreakdown = LossBreakdown(
                k = snapshot.risk.settings.balanceLossFactor,
                uncoveredShare = funding.shortageStrength,
                horizonDays = snapshot.risk.settings.horizonDays,
                items = breakdown,
            ),
        )
        logger.debug(
            "Client card built: snapshotId={}, clientId={}, services={}, lossItems={}",
            stored.id,
            id,
            card.services.size,
            card.lossBreakdown.items.size,
        )
        return card
    }

    fun getClientPosition(
        id: String,
        level: String?,
        search: String?,
        sort: String?,
        order: String?,
    ): ClientPosition {
        val stored = snapshots.findLatest() ?: throw NoSuchElementException("Клиент не найден")
        val clients = project(load(stored))
        if (clients.none { it.account.id.toString() == id }) throw NoSuchElementException("Клиент не найден")
        val sorted = filterAndSort(clients = clients, level = level, search = search, sort = sort, order = order)
        val zeroIndex = sorted.indexOfFirst { it.account.id.toString() == id }
        val position = ClientPosition(
            index = if (zeroIndex < 0) null else zeroIndex + 1,
            total = sorted.size,
            prevId = sorted.getOrNull(zeroIndex - 1)?.account?.id?.toString(),
            nextId = if (zeroIndex < 0) null else sorted.getOrNull(zeroIndex + 1)?.account?.id?.toString(),
        )
        logger.debug(
            "Client position built: snapshotId={}, clientId={}, index={}, total={}",
            stored.id,
            id,
            position.index,
            position.total,
        )
        return position
    }

    private fun filterAndSort(
        clients: List<ClientProjection>,
        level: String?,
        search: String?,
        sort: String?,
        order: String?,
    ): List<ClientProjection> {
        val normalizedLevel = level?.lowercase()
        require(normalizedLevel == null || normalizedLevel in LEVELS) { "Неизвестный уровень риска" }
        val sortField = sort ?: "score"
        require(sortField in SORT_FIELDS) { "Неизвестное поле сортировки" }
        val direction = order ?: "desc"
        require(direction == "asc" || direction == "desc") { "Порядок должен быть asc или desc" }
        val query = search?.trim()?.takeIf { it.isNotEmpty() }
        return clients.filter { client ->
            if (query != null) {
                client.account.email.contains(query, ignoreCase = true) ||
                    client.account.id.toString().contains(query, ignoreCase = true)
            } else normalizedLevel == null || client.level == normalizedLevel
        }.sortedWith(comparator(field = sortField, order = direction))
    }

    private fun lossItems(
        services: List<ru.medovik.hack2026.api.dto.request.risk.Service>,
        scored: Map<Long, ServiceRiskResult>,
        settings: RiskSettings,
        funding: ru.medovik.hack2026.api.dto.response.risk.FundingSummary,
    ): List<LossBreakdownItem> = services.filter { it.status.isLive }.mapNotNull { service ->
        val score = scored.getValue(service.id).score?.takeIf { it > 0.0 } ?: return@mapNotNull null
        val price = monthlyPrice(cents = service.price, term = service.paymentTerm)
        val risk = score / 100.0
        val share = settings.balanceLossFactor + (1.0 - settings.balanceLossFactor) * funding.shortageStrength
        LossBreakdownItem(
            serviceId = service.id.toString(),
            serviceName = service.name,
            monthlyPrice = price,
            risk = risk,
            loss = price.multiply(BigDecimal.valueOf(risk * share)).setScale(2, RoundingMode.HALF_UP),
        )
    }

    private fun load(stored: StoredBillingSnapshot): LoadedSnapshot {
        try {
            val payload = jsonMapper.readValue(stored.payload, RiskSnapshot::class.java)
            val result = riskEngine.calculate(
                snapshot = payload,
                settings = modelSettings.riskSettings(),
                evaluationTime = if (stored.live) stored.takenAt.toInstant() else calculationDate.evaluationTime(),
            )
            val assessed = mlAssessment.assess(stored, payload, result)
            return LoadedSnapshot(stored = stored, payload = payload, risk = assessed.result, mlAnnotations = assessed.annotations)
        } catch (exception: Exception) {
            logger.error(
                "Billing snapshot could not be evaluated for dashboard: snapshotId={}, takenAt={}",
                stored.id,
                stored.takenAt,
                exception,
            )
            throw exception
        }
    }

    private fun project(snapshot: LoadedSnapshot): List<ClientProjection> {
        val customers = snapshot.risk.customers.associateBy { it.accountId }
        val services = snapshot.payload.services.groupBy { it.ownerId }
        val scoredServices = snapshot.risk.services.groupBy { it.ownerId }
        return snapshot.payload.accounts.map { account ->
            val risk = customers.getValue(account.id)
            val leadingService = scoredServices[account.id].orEmpty()
                .filter { it.score != null }
                .sortedWith(compareByDescending<ServiceRiskResult> {
                    it.score
                }.thenBy { it.serviceId })
                .firstOrNull()
            val leadingFactors = leadingService?.features.orEmpty()
                .filter { it.contribution > 0 }
                .sortedWith(compareByDescending<FeatureContribution> {
                    it.contribution
                }.thenBy { it.code })
            val reasonId = leadingFactors.firstOrNull()?.code?.let { reasonId(it) }
            val loss = lossItems(
                services = services[account.id].orEmpty(),
                scored = scoredServices[account.id].orEmpty().associateBy { it.serviceId },
                settings = snapshot.risk.settings,
                funding = risk.funding,
            ).fold(ZERO) { sum, item -> sum + item.loss }
            ClientProjection(
                account = account,
                risk = risk,
                monthlyLoss = loss,
                reasonId = reasonId,
                reason = listOfNotNull(reasonId?.let { reasonLabel(it) }, leadingService?.serviceId?.let { snapshot.mlAnnotations[it] })
                    .takeIf { it.isNotEmpty() }?.joinToString("; "),
                factors = leadingFactors.mapIndexed { index, feature ->
                    ScoreFactor(
                        id = reasonId(feature.code),
                        label = factorLabel(code = feature.code, risk = risk) + if (index == 0) leadingService?.serviceId?.let { snapshot.mlAnnotations[it] }?.let { "; $it" }.orEmpty() else "",
                        points = feature.contribution.coerceIn(0.0, 100.0),
                    )
                },
            )
        }
    }

    private fun churn(current: LoadedSnapshot, history: List<StoredBillingSnapshot>): ChurnForecast {
        val atRisk = current.risk.services.filter {
            it.status.isLive && it.score != null && it.score >= current.risk.settings.mediumThreshold
        }
        if (history.size < 2) return ChurnForecast(atRiskServices = atRisk.size, weeks = emptyList())

        val monday = current.risk.evaluationTime.atZone(ZoneOffset.UTC).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val firstFact = monday.minusWeeks(8)
        val parsedHistory = history.map { stored ->
            stored to jsonMapper.readValue(stored.payload, RiskSnapshot::class.java)
        }
        val deletions = mutableMapOf<LocalDate, Int>()
        parsedHistory.zipWithNext().forEach { (newer, older) ->
            val detectedWeek = newer.first.takenAt.toInstant().atZone(ZoneOffset.UTC).toLocalDate()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            if (detectedWeek >= firstFact && detectedWeek < monday) {
                val oldStatuses = older.second.services.associate { it.id to it.status }
                val count = newer.second.services.count { service ->
                    service.status == ServiceStatus.DELETED &&
                        oldStatuses[service.id] != null &&
                        oldStatuses[service.id] != ServiceStatus.DELETED
                }
                deletions[detectedWeek] = deletions.getOrDefault(detectedWeek, 0) + count
            }
        }
        val forecasts = atRisk.groupingBy { service ->
            val date = service.nextCriticalAt?.atZone(ZoneOffset.UTC)?.toLocalDate() ?: monday
            date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).coerceAtLeast(monday)
        }.eachCount()
        val weeks = (0L..7L).map { index ->
            val start = firstFact.plusWeeks(index)
            ChurnWeek(weekStart = start, kind = "fact", services = deletions.getOrDefault(start, 0))
        } + (0L..3L).map { index ->
            val start = monday.plusWeeks(index)
            ChurnWeek(weekStart = start, kind = "forecast", services = forecasts.getOrDefault(start, 0))
        }
        return ChurnForecast(atRiskServices = atRisk.size, weeks = weeks)
    }

    private fun comparator(field: String, order: String): Comparator<ClientProjection> = Comparator { a, b ->
        if (field == "score" && (a.score == null || b.score == null)) {
            val nullOrder = compareValues(a.score == null, b.score == null)
            if (nullOrder != 0) return@Comparator nullOrder
        }
        val value = when (field) {
            "score" -> compareValues(a.score, b.score)
            "id" -> compareValues(a.account.id, b.account.id)
            "email" -> a.account.email.compareTo(b.account.email, ignoreCase = true)
            "balance" -> compareValues(a.account.balance, b.account.balance)
            "topUp" -> compareValues(a.account.totalReplenished, b.account.totalReplenished)
            else -> compareValues(a.monthlyLoss, b.monthlyLoss)
        }
        val directed = if (order == "desc") -value else value
        if (directed != 0) directed else compareValues(a.account.id, b.account.id)
    }

    private fun counts(clients: List<ClientProjection>) = LevelCounts(
        high = clients.count { it.level == "high" },
        medium = clients.count { it.level == "medium" },
        low = clients.count { it.level == "low" },
        none = clients.count { it.level == "none" },
    )

    private fun emptyDashboard() = DashboardResponse(
        snapshot = null,
        monthlyLoss = ZERO,
        riskBuckets = LEVELS.map { level ->
            RiskBucket(
                level = level,
                clients = 0,
                monthlyLoss = if (level == "none") null else ZERO,
                changed = false,
            )
        },
        topRisk = null,
        riskQueue = RiskQueue(total = 0, items = emptyList()),
        lossReasons = emptyList(),
        churn = ChurnForecast(atRiskServices = 0, weeks = emptyList()),
    )

    private fun lossChanged(client: ClientProjection, old: Map<Long, ClientProjection>): Boolean =
        old[client.account.id]?.monthlyLoss?.let { it != client.monthlyLoss } ?: false

    // The source stores a price per billing term; hourly and weekly terms use a 30-day month.
    private fun monthlyPrice(cents: Long, term: PaymentTerm): BigDecimal {
        val amount = BigDecimal.valueOf(cents)
        val monthlyCents = when (term) {
            PaymentTerm.HOUR -> amount.multiply(BigDecimal.valueOf(720))
            PaymentTerm.DAY -> amount.multiply(BigDecimal.valueOf(30))
            PaymentTerm.WEEK -> amount.multiply(BigDecimal.valueOf(30))
                .divide(BigDecimal.valueOf(7), 4, RoundingMode.HALF_UP)
            PaymentTerm.MONTH -> amount
            PaymentTerm.QUARTER_YEAR -> amount.divide(BigDecimal.valueOf(3), 4, RoundingMode.HALF_UP)
            PaymentTerm.HALF_YEAR -> amount.divide(BigDecimal.valueOf(6), 4, RoundingMode.HALF_UP)
            PaymentTerm.YEAR -> amount.divide(BigDecimal.valueOf(12), 4, RoundingMode.HALF_UP)
        }
        return monthlyCents.movePointLeft(2).setScale(2, RoundingMode.HALF_UP)
    }

    private fun euros(cents: Long): BigDecimal =
        BigDecimal.valueOf(cents, 2).setScale(2, RoundingMode.HALF_UP)

    private fun reasonId(code: String): String = when (code) {
        "F1" -> "no-auto-renew"
        "F2" -> "low-balance"
        "F3" -> "awaiting-payment"
        "F4" -> "awaiting-payment-no-funds"
        "F5" -> "disabled"
        else -> code.lowercase()
    }

    private fun reasonLabel(id: String): String = when (id) {
        "no-auto-renew" -> "Истекает без автопродления"
        "low-balance" -> "Не хватает средств на продление"
        "awaiting-payment" -> "Ожидает оплаты"
        "awaiting-payment-no-funds" -> "Ожидает оплаты, не хватает средств"
        "disabled" -> "Выключена клиентом"
        else -> "Фактор риска $id"
    }

    private fun factorLabel(code: String, risk: CustomerRiskResult): String =
        if (code == "F2" || code == "F4") {
            "На счёте не хватает ${euros(risk.funding.shortfall)} € на продление"
        } else reasonLabel(reasonId(code))

    private fun <T> List<T>.sumMoney(amount: (T) -> BigDecimal): BigDecimal =
        fold(ZERO) { sum, item -> sum + amount(item) }

    private data class LoadedSnapshot(
        val stored: StoredBillingSnapshot,
        val payload: RiskSnapshot,
        val risk: RiskResult,
        val mlAnnotations: Map<Long, String> = emptyMap(),
    )

    private data class ClientProjection(
        val account: Account,
        val risk: CustomerRiskResult,
        val monthlyLoss: BigDecimal,
        val reasonId: String?,
        val reason: String?,
        val factors: List<ScoreFactor>,
    ) {
        val score: Double? get() = risk.attentionScore
        val level: String get() = risk.level.name.lowercase()
    }

    private companion object {
        val ZERO: BigDecimal = BigDecimal.ZERO.setScale(2)
        val LEVELS = listOf("high", "medium", "low", "none")
        val SORT_FIELDS = setOf("score", "id", "email", "balance", "topUp", "monthlyLoss")
    }
}
