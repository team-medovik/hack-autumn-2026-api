package ru.medovik.hack2026.api.service.settings

import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import ru.medovik.hack2026.api.dto.request.risk.RiskSettings
import ru.medovik.hack2026.api.dto.request.risk.RiskSnapshot
import ru.medovik.hack2026.api.dto.settings.FinanceSettings
import ru.medovik.hack2026.api.dto.settings.ForecastSettings
import ru.medovik.hack2026.api.dto.settings.ModelSettings
import ru.medovik.hack2026.api.dto.settings.ModelSettingsValues
import ru.medovik.hack2026.api.dto.settings.PatchModelSettingsRequest
import ru.medovik.hack2026.api.dto.settings.RiskThresholds
import ru.medovik.hack2026.api.dto.settings.SaveModelSettingsRequest
import ru.medovik.hack2026.api.exception.InvalidModelSettingsException
import ru.medovik.hack2026.api.exception.ModelSettingsConflictException
import ru.medovik.hack2026.api.repository.dashboard.BillingSnapshotRepository
import ru.medovik.hack2026.api.repository.settings.ModelSettingsRepository
import ru.medovik.hack2026.api.repository.settings.CalculationDateRepository
import ru.medovik.hack2026.api.realtime.EntityChangedEvent
import ru.medovik.hack2026.api.realtime.RealtimeEntity
import ru.medovik.hack2026.api.service.risk.RiskEngine
import ru.medovik.hack2026.api.service.risk.feature.RiskFeatureRegistry
import ru.medovik.hack2026.api.service.ml.MlAssessmentService
import tools.jackson.databind.json.JsonMapper
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.floor

@Service
class ModelSettingsService(
    private val repository: ModelSettingsRepository,
    private val snapshots: BillingSnapshotRepository,
    private val jsonMapper: JsonMapper,
    private val calculationDate: CalculationDateRepository,
    private val events: ApplicationEventPublisher,
    private val mlAssessment: MlAssessmentService,
) {
    private val riskEngine = RiskEngine()
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun get(): ModelSettings {
        val stored = repository.current()
        logger.debug("Model settings read: version={}, updatedAt={}, storedWeightKeys={}",
            stored.version, stored.updatedAt, stored.values.factorWeights.keys)
        return response(
            version = stored.version,
            updatedAt = stored.updatedAt,
            updatedBy = stored.updatedBy,
            values = withRegisteredWeights(stored.values),
        )
    }

    @Transactional
    fun save(request: SaveModelSettingsRequest, updatedBy: String): ModelSettings {
        logger.debug("Model settings save requested: expectedVersion={}, updatedBy={}, weights={}",
            request.version, updatedBy, request.values.factorWeights)
        repository.lockForSave()
        val current = repository.current()
        logger.debug("Model settings save lock acquired: expectedVersion={}, currentVersion={}",
            request.version, current.version)
        if (request.version != current.version) {
            logger.warn(
                "Model settings version conflict: requestedVersion={}, currentVersion={}, updatedBy={}",
                request.version,
                current.version,
                updatedBy,
            )
            throw ModelSettingsConflictException(
                current = response(
                    version = current.version,
                    updatedAt = current.updatedAt,
                    updatedBy = current.updatedBy,
                    values = withRegisteredWeights(current.values),
                ),
            )
        }
        validateWeightKeys(request.values.factorWeights.keys)
        val values = withRegisteredWeights(request.values)
        validate(values)
        logger.debug("Model settings validated: version={}, weights={}, thresholds={}, finance={}, forecast={}",
            current.version, values.factorWeights, values.riskThresholds, values.finance, values.forecast)
        val version = current.version + 1
        val updatedAt = OffsetDateTime.now(ZoneOffset.UTC)
        val result = response(
            version = version,
            updatedAt = updatedAt,
            updatedBy = updatedBy,
            values = values,
        )
        logger.debug("Model settings recalculated before insert: newVersion={}, scoreCount={}",
            version, result.scoreHistogram.sum())
        repository.insert(version = version, updatedAt = updatedAt, updatedBy = updatedBy, values = values)
        logger.debug("Model settings row inserted: version={}, updatedBy={}", version, updatedBy)
        events.publishEvent(EntityChangedEvent(RealtimeEntity.MODEL_SETTINGS))
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                logger.info(
                    "Model settings saved: version={}, previousVersion={}, updatedBy={}",
                    version,
                    current.version,
                    updatedBy,
                )
            }

            override fun afterCompletion(status: Int) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    logger.warn("Model settings transaction did not commit: version={}, status={}", version, status)
                }
            }
        })
        return result
    }

    /**
     * The row lock covers read, merge and write. Legacy PUT and contract PATCH share
     * optimistic version checks, validation and database persistence.
     */
    @Transactional
    fun patch(request: PatchModelSettingsRequest, updatedBy: String): ModelSettings {
        logger.info("Model settings PATCH requested: expectedVersion={}, updatedBy={}, changes={}",
            request.version, updatedBy, request.changes)
        if (request.version < 1L) throw InvalidModelSettingsException("version должен быть положительным")
        repository.lockForSave()
        val current = repository.current()
        logger.debug("Model settings PATCH lock acquired: expectedVersion={}, currentVersion={}",
            request.version, current.version)
        if (request.version != current.version) {
            logger.warn("Model settings PATCH version conflict: expectedVersion={}, currentVersion={}, updatedBy={}",
                request.version, current.version, updatedBy)
            throw ModelSettingsConflictException(
                current = response(
                    version = current.version,
                    updatedAt = current.updatedAt,
                    updatedBy = current.updatedBy,
                    values = withRegisteredWeights(current.values),
                ),
            )
        }
        request.changes.factorWeights?.keys?.let { validateWeightKeys(it) }
        val merged = request.changes.mergeInto(withRegisteredWeights(current.values))
        logger.debug("Model settings PATCH merged: version={}, weights={}, thresholds={}, finance={}, forecast={}",
            current.version, merged.factorWeights, merged.riskThresholds, merged.finance, merged.forecast)
        return save(
            request = SaveModelSettingsRequest(version = current.version, values = merged),
            updatedBy = updatedBy,
        )
    }

    @Transactional(readOnly = true)
    fun riskSettings(): RiskSettings {
        val stored = repository.current()
        logger.debug("Risk settings requested from model version={}", stored.version)
        return toRiskSettings(withRegisteredWeights(stored.values))
    }

    private fun withRegisteredWeights(values: ModelSettingsValues): ModelSettingsValues {
        val missing = RiskFeatureRegistry.keys - values.factorWeights.keys
        val unknown = values.factorWeights.keys - RiskFeatureRegistry.keys
        if (missing.isNotEmpty()) logger.debug("Stored model settings missing registered weights; defaults applied: {}", missing)
        if (unknown.isNotEmpty()) logger.warn("Stored model settings contain unregistered weights; ignored keys={}", unknown)
        return values.copy(
            factorWeights = RiskFeatureRegistry.defaultWeights + values.factorWeights.filterKeys { it in RiskFeatureRegistry.keys },
        )
    }

    private fun validateWeightKeys(keys: Set<String>) {
        val unknown = keys - RiskFeatureRegistry.keys
        if (unknown.isNotEmpty()) {
            logger.warn("Model settings rejected: unregistered weight keys={}; registeredKeys={}",
                unknown.sorted(), RiskFeatureRegistry.keys)
            throw InvalidModelSettingsException("Неизвестные ключи factorWeights: ${unknown.sorted().joinToString()}")
        }
    }

    private fun response(
        version: Long,
        updatedAt: OffsetDateTime,
        updatedBy: String,
        values: ModelSettingsValues,
    ): ModelSettings {
        val stored = snapshots.findLatest()
        val histogram = MutableList(101) { 0 }
        if (stored != null) {
            val startedAt = System.nanoTime()
            val snapshot = jsonMapper.readValue(stored.payload, RiskSnapshot::class.java)
            val result = riskEngine.calculate(
                snapshot = snapshot,
                settings = toRiskSettings(values),
                evaluationTime = if (stored.live) stored.takenAt.toInstant() else calculationDate.get().let { date ->
                    if (date.mode == "fixed") requireNotNull(date.fixedAt).toInstant() else java.time.Instant.now()
                },
            )
            val assessed = mlAssessment.assess(stored, snapshot, result).result
            assessed.customers.mapNotNull { it.attentionScore }.forEach { score ->
                histogram[floor(score).toInt().coerceIn(0, 100)]++
            }
            logger.debug("Model settings histogram calculated: version={}, snapshotId={}, customers={}, scoredCustomers={}, elapsedMs={}",
                version, stored.id, result.customers.size, histogram.sum(),
                (System.nanoTime() - startedAt) / 1_000_000)
        } else {
            logger.debug("Model settings histogram empty: version={}, reason=no_snapshot", version)
        }
        return ModelSettings(
            version = version,
            updatedAt = updatedAt,
            updatedBy = updatedBy,
            values = values,
            defaults = DEFAULTS,
            featureDefinitions = RiskFeatureRegistry.definitions,
            scoreHistogram = histogram,
        )
    }

    private fun validate(values: ModelSettingsValues) {
        val weights = values.factorWeights
        validateWeightKeys(weights.keys)
        val invalidWeights = weights.filterValues { it !in 0..100 }
        if (invalidWeights.isNotEmpty()) {
            logger.warn("Model settings rejected: weights outside 0..100={}", invalidWeights)
            throw InvalidModelSettingsException("Веса должны быть от 0 до 100")
        }
        val thresholds = values.riskThresholds
        if (thresholds.medium !in 1..100 || thresholds.high !in 1..100 || thresholds.high <= thresholds.medium) {
            logger.warn("Model settings rejected: invalid thresholds medium={}, high={}",
                thresholds.medium, thresholds.high)
            throw InvalidModelSettingsException("Пороги должны удовлетворять 0 < medium < high <= 100")
        }
        val factor = values.finance.balanceLossFactor
        if (!factor.isFinite() || factor !in 0.0..1.0 ||
            kotlin.math.abs(factor * 20 - kotlin.math.round(factor * 20)) > 1e-9
        ) {
            logger.warn("Model settings rejected: invalid balanceLossFactor={}", factor)
            throw InvalidModelSettingsException("balanceLossFactor должен быть кратен 0.05 и находиться в диапазоне 0..1")
        }
        if (values.forecast.horizonDays !in 1..30 || values.forecast.recalculationIntervalMinutes !in 1..60) {
            logger.warn("Model settings rejected: invalid forecast horizonDays={}, recalculationIntervalMinutes={}",
                values.forecast.horizonDays, values.forecast.recalculationIntervalMinutes)
            throw InvalidModelSettingsException("Параметры прогноза вне допустимых границ")
        }
    }

    private fun toRiskSettings(values: ModelSettingsValues): RiskSettings = RiskSettings(
        horizonDays = values.forecast.horizonDays,
        weights = RiskFeatureRegistry.entries.associate { registered ->
            registered.feature.code to values.factorWeights.getValue(registered.definition.key).toDouble()
        },
        mediumThreshold = values.riskThresholds.medium.toDouble(),
        highThreshold = values.riskThresholds.high.toDouble(),
        bonusesAsMoney = values.finance.bonusesAsMoney,
        referralBalanceAsMoney = values.finance.referralBalanceAsMoney,
        balanceLossFactor = values.finance.balanceLossFactor,
    )

    private companion object {
        val DEFAULTS = ModelSettingsValues(
            factorWeights = RiskFeatureRegistry.defaultWeights,
            riskThresholds = RiskThresholds(medium = 40, high = 70),
            finance = FinanceSettings(
                bonusesAsMoney = true,
                referralBalanceAsMoney = false,
                balanceLossFactor = 0.5,
            ),
            forecast = ForecastSettings(horizonDays = 5, recalculationIntervalMinutes = 5),
        )
    }
}
