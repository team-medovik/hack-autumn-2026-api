package ru.medovik.hack2026.api.service.risk.feature.impl

import ru.medovik.hack2026.api.dto.request.risk.ServiceStatus
import ru.medovik.hack2026.api.service.risk.feature.RiskFeature
import ru.medovik.hack2026.api.service.risk.feature.util.FeatureContext
import ru.medovik.hack2026.api.service.risk.feature.util.FeatureEvaluation

class ExpiryWithoutAutoRenew : RiskFeature {
    override val code = "F1"

    override fun evaluate(context: FeatureContext): FeatureEvaluation = with(context) {
        if (service.status !in setOf(ServiceStatus.ACTIVE, ServiceStatus.SUSPENDED) ||
            service.autoProlong || !inHorizon
        ) return FeatureEvaluation.notApplicable()

        FeatureEvaluation(
            applicable = true,
            strength = (1 - daysToExpiry.coerceAtLeast(0.0) / settings.horizonDays).coerceIn(0.0, 1.0),
            explanation = if (daysToExpiry < 0) {
                "У услуги ${service.name} срок истёк, статус не обновлён; автопродление выключено"
            } else {
                "Услуга ${service.name} истекает ${service.expiresAt}, автопродление выключено"
            },
            evidence = mapOf(
                "expiresAt" to service.expiresAt.toString(),
                "daysToExpiry" to daysToExpiry.toString(),
                "autoProlong" to "false",
                "horizonDays" to settings.horizonDays.toString(),
            ),
        )
    }
}
