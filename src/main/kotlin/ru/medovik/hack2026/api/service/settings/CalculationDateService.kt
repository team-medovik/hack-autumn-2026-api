package ru.medovik.hack2026.api.service.settings

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import ru.medovik.hack2026.api.dto.request.risk.RiskSnapshot
import ru.medovik.hack2026.api.dto.settings.CalculationDate
import ru.medovik.hack2026.api.repository.dashboard.BillingSnapshotRepository
import ru.medovik.hack2026.api.repository.settings.CalculationDateRepository
import ru.medovik.hack2026.api.service.risk.RiskEngine
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.ZoneOffset

@Service
class CalculationDateService(
    private val repository: CalculationDateRepository,
    private val snapshots: BillingSnapshotRepository,
    private val modelSettings: ModelSettingsService,
    private val jsonMapper: JsonMapper,
) {
    private val riskEngine = RiskEngine()
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun get(): CalculationDate = repository.get()

    @Transactional(readOnly = true)
    fun evaluationTime(): Instant = evaluationTime(repository.get())

    @Transactional
    fun save(request: CalculationDate): CalculationDate {
        require(request.mode == "current" || request.mode == "fixed") { "Неизвестный режим даты расчёта" }
        require(request.mode != "fixed" || request.fixedAt != null) { "Для фиксированной даты требуется fixedAt" }
        val normalized = request.copy(fixedAt = request.fixedAt?.withOffsetSameInstant(ZoneOffset.UTC))
        val snapshot = snapshots.findLatest()
        snapshot?.let { stored ->
            riskEngine.calculate(
                snapshot = jsonMapper.readValue(stored.payload, RiskSnapshot::class.java),
                settings = modelSettings.riskSettings(),
                evaluationTime = evaluationTime(normalized),
            )
        }
        repository.save(normalized)
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                logger.info(
                    "Calculation date saved: mode={}, fixedAt={}, snapshotId={}, recalculated={}",
                    normalized.mode,
                    normalized.fixedAt,
                    snapshot?.id,
                    snapshot != null,
                )
            }
        })
        return normalized
    }

    private fun evaluationTime(value: CalculationDate): Instant =
        if (value.mode == "fixed") requireNotNull(value.fixedAt).toInstant() else Instant.now()
}
