package ru.medovik.hack2026.api.service.risk.feature

import org.slf4j.LoggerFactory
import ru.medovik.hack2026.api.dto.settings.FeatureDefinition
import ru.medovik.hack2026.api.service.risk.feature.impl.AwaitingRenewal
import ru.medovik.hack2026.api.service.risk.feature.impl.AwaitingRenewalWithoutFunds
import ru.medovik.hack2026.api.service.risk.feature.impl.BonusesExceedMoney
import ru.medovik.hack2026.api.service.risk.feature.impl.ExpiryWithoutAutoRenew
import ru.medovik.hack2026.api.service.risk.feature.impl.InsufficientFunds
import ru.medovik.hack2026.api.service.risk.feature.impl.SuspendedService

/** Register a feature here to expose its slider, default weight and calculation together. */
object RiskFeatureRegistry {
    private val logger = LoggerFactory.getLogger(RiskFeatureRegistry::class.java)

    val entries: List<RegisteredRiskFeature> = listOf(
        RegisteredRiskFeature(
            AwaitingRenewal(),
            FeatureDefinition("awaitingPayment", "Ожидает оплаты", "Услуга ожидает оплаты после окончания срока действия."),
            70,
        ),
        RegisteredRiskFeature(
            InsufficientFunds(),
            FeatureDefinition("insufficientFunds", "Не хватает средств на продление", "Средств на счёте недостаточно для продлений в горизонте прогноза."),
            40,
        ),
        RegisteredRiskFeature(
            ExpiryWithoutAutoRenew(),
            FeatureDefinition("expiringWithoutAutoRenewal", "Истекает без автопродления", "Срок услуги истекает в горизонте прогноза, а автопродление отключено."),
            50,
        ),
        RegisteredRiskFeature(
            AwaitingRenewalWithoutFunds(),
            FeatureDefinition("awaitingPaymentWithoutFunds", "Ожидает оплаты, не хватает средств", "Услуга ожидает оплаты, и средств на её продление недостаточно."),
            30,
        ),
        RegisteredRiskFeature(
            SuspendedService(),
            FeatureDefinition("disabledByClient", "Выключена клиентом", "Клиент приостановил услугу."),
            15,
        ),
        RegisteredRiskFeature(
            BonusesExceedMoney(),
            FeatureDefinition("bonusesExceedMoney", "Бонусов больше денег", "Бонусный баланс превышает денежный баланс клиента."),
            10,
        ),
    )

    val definitions: List<FeatureDefinition> = entries.map { it.definition }
    val defaultWeights: Map<String, Int> = entries.associate { it.definition.key to it.defaultWeight }
    val keys: Set<String> = defaultWeights.keys
    val byCode: Map<String, RegisteredRiskFeature> = entries.associateBy { it.feature.code }

    init {
        require(entries.map { it.feature.code }.distinct().size == entries.size) { "Duplicate feature codes" }
        require(keys.size == entries.size) { "Duplicate feature keys" }
        require(definitions.all { it.key.matches(Regex("^[a-z][a-zA-Z0-9]*$")) }) { "Invalid feature key" }
        require(entries.all { it.defaultWeight in 0..100 }) { "Invalid default weight" }
        logger.info(
            "Risk features registered: count={}, displayOrder={}, codeMapping={}, defaultWeights={}",
            entries.size,
            definitions.map { it.key },
            entries.associate { it.definition.key to it.feature.code },
            defaultWeights,
        )
    }
}
