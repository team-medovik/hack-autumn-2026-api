package ru.medovik.hack2026.api.service.risk

import ru.medovik.hack2026.api.dto.request.risk.PaymentTerm
import ru.medovik.hack2026.api.dto.request.risk.RiskSnapshot
import ru.medovik.hack2026.api.dto.request.risk.ServiceStatus
import ru.medovik.hack2026.api.dto.response.risk.RiskResult
import java.time.Duration

/** Product policy, not a calibrated probability or a second set of F1–F6 weights. */
class HybridRiskAdjuster {
    private val aggregator = CustomerRiskAggregator()

    fun apply(base: RiskResult, snapshot: RiskSnapshot, probabilities: Map<Long, Double>): RiskResult {
        if (probabilities.isEmpty()) return base
        val source = snapshot.services.associateBy { it.id }
        val services = base.services.map { result ->
            val original = result.score ?: return@map result
            val probability = probabilities[result.serviceId] ?: return@map result
            val service = source[result.serviceId] ?: return@map result
            if (!probability.isFinite() || probability !in 0.0..1.0 ||
                service.status !in setOf(ServiceStatus.ACTIVE, ServiceStatus.SUSPENDED) ||
                service.paymentTerm !in setOf(PaymentTerm.HOUR, PaymentTerm.DAY, PaymentTerm.MONTH) ||
                service.ownerId != result.ownerId ||
                service.createdAt > base.evaluationTime || service.expiresAt <= base.evaluationTime ||
                service.expiresAt > base.evaluationTime.plus(Duration.ofDays(7)) ||
                result.dataQualityWarnings.isNotEmpty()
            ) return@map result
            val adjusted = (original + adjustment(original, probability)).coerceIn(0.0, 100.0)
            result.copy(
                score = adjusted,
                level = base.settings.level(adjusted),
                loyaltyScore = 100.0 - adjusted,
                // rawScore/features remain the original rule explanation.
                revenueExposure = if (adjusted >= base.settings.mediumThreshold &&
                    service.expiresAt <= base.evaluationTime.plus(Duration.ofDays(base.settings.horizonDays.toLong()))
                ) service.price else 0L,
            )
        }
        val grouped = services.groupBy { it.ownerId }
        return base.copy(
            services = services,
            customers = base.customers.map { customer ->
                aggregator.aggregate(customer.accountId, grouped[customer.accountId].orEmpty(),
                    customer.funding, base.settings)
            },
        )
    }

    companion object {
        const val INFLUENCE = 0.3
        const val MAX_ADJUSTMENT = 20.0

        fun adjustment(algorithmScore: Double, probability: Double): Double {
            require(algorithmScore.isFinite() && algorithmScore in 0.0..100.0)
            require(probability.isFinite() && probability in 0.0..1.0)
            return (INFLUENCE * (100.0 * probability - algorithmScore)).coerceIn(-MAX_ADJUSTMENT, MAX_ADJUSTMENT)
        }
    }
}
