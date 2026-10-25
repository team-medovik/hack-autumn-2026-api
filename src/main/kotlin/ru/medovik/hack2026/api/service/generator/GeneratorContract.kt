package ru.medovik.hack2026.api.service.generator

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import ru.medovik.hack2026.api.dto.request.risk.PaymentTerm
import ru.medovik.hack2026.api.dto.request.risk.ServiceStatus
import java.time.Instant

@JsonIgnoreProperties(ignoreUnknown = true)
data class GeneratorStatus(val generator: GeneratorRun)
@JsonIgnoreProperties(ignoreUnknown = true)
data class GeneratorRun(val runId: String)

data class GeneratorPage(
    val runId: String, val revision: Long, val asOf: Instant,
    val events: List<SequencedEvent>, val nextSequence: Long, val hasMore: Boolean,
) {
    fun validate(after: Long) {
        require(runId.isNotBlank() && runId.length <= 255 && revision >= 0 && after >= 0)
        require(events.size <= 1000)
        require(nextSequence == Math.addExact(after, events.size.toLong()))
        require(!hasMore || events.isNotEmpty())
        events.forEachIndexed { index, item ->
            require(item.sequence == Math.addExact(after, index.toLong() + 1)) { "Event sequence gap" }
            item.event.validate()
            require(item.event.recordedAt <= asOf)
        }
        require(events.zipWithNext().all { (a, b) -> a.event.occurredAt <= b.event.occurredAt })
    }
}
data class SequencedEvent(val sequence: Long, val event: BillingEvent)

data class BillingEvent(
    val id: String, val type: String, val occurredAt: Instant, val recordedAt: Instant,
    val accountId: Long, val serviceId: Long?, val currency: String, val source: String,
    val causationId: String?, val email: String? = null, val name: String? = null,
    val amountMinor: Long? = null, val balanceBeforeMinor: Long? = null, val balanceAfterMinor: Long? = null,
    val paymentTerm: PaymentTerm? = null, val priceMinor: Long? = null, val tariffCode: String? = null,
    val autoProlong: Boolean? = null, val enabled: Boolean? = null, val mode: String? = null,
    val dueAt: Instant? = null, val paidThrough: Instant? = null, val toStatus: ServiceStatus? = null,
    val reason: String? = null, val category: String? = null,
    val replacementServiceId: Long? = null, val action: String? = null,
) {
    fun validate() {
        require(id.isNotBlank() && id.length <= 255 && accountId > 0 && currency == "EUR")
        require(source in setOf("synthetic", "observed") && recordedAt >= occurredAt)
        require(serviceId == null || serviceId > 0)
        require(listOfNotNull(amountMinor, priceMinor, balanceBeforeMinor, balanceAfterMinor).all { it >= 0 })
        when (type) {
            "account_created" -> require(serviceId == null && !email.isNullOrBlank())
            "wallet_topped_up" -> require(serviceId == null && amountMinor != null)
            "service_created" -> require(serviceId != null && !name.isNullOrBlank() &&
                paymentTerm in setOf(PaymentTerm.HOUR, PaymentTerm.DAY, PaymentTerm.MONTH) &&
                priceMinor != null && autoProlong != null && !tariffCode.isNullOrBlank())
            "payment_succeeded", "payment_failed" -> {
                require(serviceId != null && amountMinor != null && dueAt != null &&
                    mode in setOf("purchase", "manual", "auto"))
                if (type == "payment_succeeded") require(paidThrough != null && paidThrough >= occurredAt)
                else require(paidThrough == null)
            }
            "auto_prolong_changed" -> require(serviceId != null && enabled != null)
            "status_changed" -> require(serviceId != null && toStatus != null)
            "replacement_confirmed" -> require(serviceId != null && replacementServiceId != null &&
                replacementServiceId > 0 && replacementServiceId != serviceId && dueAt != null)
            "service_restricted", "service_released" -> require(serviceId != null &&
                category == "nonfinancial" && !reason.isNullOrBlank() && toStatus != null)
            "renewal_paused", "renewal_due" -> require(serviceId != null && dueAt != null)
            "action_rejected" -> require(!action.isNullOrBlank() && !reason.isNullOrBlank())
            else -> error("Unknown event type")
        }
    }
}
