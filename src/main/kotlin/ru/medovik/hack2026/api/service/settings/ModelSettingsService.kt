package ru.medovik.hack2026.api.service.settings

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import ru.medovik.hack2026.api.dto.request.risk.RiskSettings
import ru.medovik.hack2026.api.dto.request.risk.RiskSnapshot
import ru.medovik.hack2026.api.dto.settings.FactorWeights
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
import ru.medovik.hack2026.api.service.risk.RiskEngine
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
) {
    private val riskEngine = RiskEngine()
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional(readOnly = true)
    fun get(): ModelSettings {
        val stored = repository.current()
        return response(
            version = stored.version,
            updatedAt = stored.updatedAt,
            updatedBy = stored.updatedBy,
            values = stored.values,
        )
    }

    @Transactional
    fun save(request: SaveModelSettingsRequest, updatedBy: String): ModelSettings {
        repository.lockForSave()
        val current = repository.current()
        if (request.version != current.version) {
            logger.info(
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
                    values = current.values,
                ),
            )
        }
        validate(request.values)
        val version = current.version + 1
        val updatedAt = OffsetDateTime.now(ZoneOffset.UTC)
        val result = response(
            version = version,
            updatedAt = updatedAt,
            updatedBy = updatedBy,
            values = request.values,
        )
        repository.insert(version = version, updatedAt = updatedAt, updatedBy = updatedBy, values = request.values)
        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCommit() {
                logger.info(
                    "Model settings saved: version={}, previousVersion={}, updatedBy={}",
                    version,
                    current.version,
                    updatedBy,
                )
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
        if (request.version < 1L) throw InvalidModelSettingsException("version должен быть положительным")
        repository.lockForSave()
        val current = repository.current()
        if (request.version != current.version) {
            throw ModelSettingsConflictException(
                current = response(
                    version = current.version,
                    updatedAt = current.updatedAt,
                    updatedBy = current.updatedBy,
                    values = current.values,
                ),
            )
        }
        val merged = request.changes.mergeInto(current.values)
        return save(
            request = SaveModelSettingsRequest(version = current.version, values = merged),
            updatedBy = updatedBy,
        )
    }

    @Transactional(readOnly = true)
    fun riskSettings(): RiskSettings = toRiskSettings(repository.current().values)

    private fun response(
        version: Long,
        updatedAt: OffsetDateTime,
        updatedBy: String,
        values: ModelSettingsValues,
    ): ModelSettings {
        val stored = snapshots.findLatest()
        val histogram = MutableList(101) { 0 }
        if (stored != null) {
            val snapshot = jsonMapper.readValue(stored.payload, RiskSnapshot::class.java)
            val result = riskEngine.calculate(
                snapshot = snapshot,
                settings = toRiskSettings(values),
                evaluationTime = calculationDate.get().let { date ->
                    if (date.mode == "fixed") requireNotNull(date.fixedAt).toInstant() else java.time.Instant.now()
                },
            )
            result.customers.mapNotNull { it.attentionScore }.forEach { score ->
                histogram[floor(score).toInt().coerceIn(0, 100)]++
            }
        }
        return ModelSettings(
            version = version,
            updatedAt = updatedAt,
            updatedBy = updatedBy,
            values = values,
            defaults = DEFAULTS,
            scoreHistogram = histogram,
        )
    }

    private fun validate(values: ModelSettingsValues) {
        val weights = values.factorWeights
        val allWeights = listOf(
            weights.awaitingPayment,
            weights.expiringWithoutAutoRenewal,
            weights.insufficientFunds,
            weights.awaitingPaymentWithoutFunds,
            weights.disabledByClient,
            weights.bonusesExceedMoney,
        )
        if ((allWeights + weights.additionalWeights.values).any { it !in 0..100 }) throw InvalidModelSettingsException("Веса должны быть от 0 до 100")
        val thresholds = values.riskThresholds
        if (thresholds.medium !in 1..100 || thresholds.high !in 1..100 || thresholds.high <= thresholds.medium) {
            throw InvalidModelSettingsException("Пороги должны удовлетворять 0 < medium < high <= 100")
        }
        val factor = values.finance.balanceLossFactor
        if (!factor.isFinite() || factor !in 0.0..1.0 ||
            kotlin.math.abs(factor * 20 - kotlin.math.round(factor * 20)) > 1e-9
        ) {
            throw InvalidModelSettingsException("balanceLossFactor должен быть кратен 0.05 и находиться в диапазоне 0..1")
        }
        if (values.forecast.horizonDays !in 1..30 || values.forecast.recalculationIntervalMinutes !in 1..60) {
            throw InvalidModelSettingsException("Параметры прогноза вне допустимых границ")
        }
    }

    private fun toRiskSettings(values: ModelSettingsValues): RiskSettings = RiskSettings(
        horizonDays = values.forecast.horizonDays,
        weights = mapOf(
            "F1" to values.factorWeights.expiringWithoutAutoRenewal.toDouble(),
            "F2" to values.factorWeights.insufficientFunds.toDouble(),
            "F3" to values.factorWeights.awaitingPayment.toDouble(),
            "F4" to values.factorWeights.awaitingPaymentWithoutFunds.toDouble(),
            "F5" to values.factorWeights.disabledByClient.toDouble(),
            "F6" to values.factorWeights.bonusesExceedMoney.toDouble(),
        ) + values.factorWeights.additionalWeights.mapValues { it.value.toDouble() },
        mediumThreshold = values.riskThresholds.medium.toDouble(),
        highThreshold = values.riskThresholds.high.toDouble(),
        bonusesAsMoney = values.finance.bonusesAsMoney,
        referralBalanceAsMoney = values.finance.referralBalanceAsMoney,
        balanceLossFactor = values.finance.balanceLossFactor,
    )

    private companion object {
        val DEFAULTS = ModelSettingsValues(
            factorWeights = FactorWeights(
                awaitingPayment = 70,
                expiringWithoutAutoRenewal = 50,
                insufficientFunds = 40,
                awaitingPaymentWithoutFunds = 30,
                disabledByClient = 15,
                bonusesExceedMoney = 10,
            ),
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
