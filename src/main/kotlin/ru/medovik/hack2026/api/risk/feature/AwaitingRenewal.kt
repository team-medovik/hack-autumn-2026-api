package ru.medovik.hack2026.api.risk.feature

import ru.medovik.hack2026.api.risk.model.ServiceStatus
import java.time.Duration

class AwaitingRenewal : RiskFeature {
    override val code = "F3"

    override fun evaluate(context: FeatureContext): FeatureEvaluation = with(context) {
        if (service.status != ServiceStatus.PROLONG_WAIT) return FeatureEvaluation.notApplicable()
        val expectedDeletionAt = service.expiresAt.plus(Duration.ofDays(5))
        FeatureEvaluation(
            applicable = true,
            strength = 1.0,
            explanation = if (!evaluationTime.isBefore(expectedDeletionAt)) {
                "Услуга ${service.name} ожидает оплаты; расчётный срок удаления $expectedDeletionAt уже наступил"
            } else {
                "Услуга ${service.name} ожидает оплаты; расчётный срок удаления $expectedDeletionAt"
            },
            evidence = mapOf(
                "status" to "prolong_wait",
                "expectedDeletionAt" to expectedDeletionAt.toString(),
            ),
        )
    }
}
