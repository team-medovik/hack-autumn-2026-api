package ru.medovik.hack2026.api.service.risk.feature.impl

import ru.medovik.hack2026.api.dto.request.risk.ServiceStatus
import ru.medovik.hack2026.api.service.risk.feature.RiskFeature
import ru.medovik.hack2026.api.service.risk.feature.util.FeatureContext
import ru.medovik.hack2026.api.service.risk.feature.util.FeatureEvaluation

class SuspendedService : RiskFeature {
    override val code = "F5"

    override fun evaluate(context: FeatureContext): FeatureEvaluation = with(context) {
        if (service.status != ServiceStatus.SUSPENDED) return FeatureEvaluation.notApplicable()
        FeatureEvaluation(
            applicable = true,
            strength = 1.0,
            explanation = "Услуга ${service.name} выключена клиентом (гипотеза риска)",
            evidence = mapOf("status" to "suspended", "source" to "team_hypothesis"),
        )
    }
}
