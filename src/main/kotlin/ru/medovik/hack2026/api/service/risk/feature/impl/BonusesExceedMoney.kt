package ru.medovik.hack2026.api.service.risk.feature.impl

import ru.medovik.hack2026.api.dto.request.risk.BonusState
import ru.medovik.hack2026.api.service.risk.feature.RiskFeature
import ru.medovik.hack2026.api.service.risk.feature.util.FeatureContext
import ru.medovik.hack2026.api.service.risk.feature.util.FeatureEvaluation

class BonusesExceedMoney : RiskFeature {
    override val code = "F6"

    override fun evaluate(context: FeatureContext): FeatureEvaluation = with(context) {
        val bonus = if (account.bonusState == BonusState.UNLOCKED) account.bonusBalance else 0L
        val excess = (bonus - account.balance).coerceAtLeast(0L)
        if (excess == 0L) return FeatureEvaluation.notApplicable()
        FeatureEvaluation(
            applicable = true,
            strength = excess.toDouble() / bonus.toDouble(),
            explanation = "У клиента бонусов больше, чем денег на счёте",
            evidence = mapOf("bonusBalance" to bonus.toString(), "balance" to account.balance.toString()),
        )
    }
}
