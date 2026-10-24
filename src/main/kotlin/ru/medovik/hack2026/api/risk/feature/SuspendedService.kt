package ru.medovik.hack2026.api.risk.feature

import ru.medovik.hack2026.api.risk.model.ServiceStatus

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
