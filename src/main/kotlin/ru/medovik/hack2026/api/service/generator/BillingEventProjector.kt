package ru.medovik.hack2026.api.service.generator

import ru.medovik.hack2026.api.dto.request.risk.*

/** Mirror of the generator's observable reducer; no generator plans or ML involved. */
class BillingEventProjector {
    fun apply(snapshot: RiskSnapshot, events: List<BillingEvent>): RiskSnapshot {
        val accounts = snapshot.accounts.associateBy { it.id }.toMutableMap()
        val services = snapshot.services.associateBy { it.id }.toMutableMap()
        events.forEach { event ->
            event.validate()
            val aid = event.accountId
            if (event.type == "account_created") {
                check(aid !in accounts) { "Repeated account creation" }
                accounts[aid] = Account(aid, requireNotNull(event.email), 0, 0, 0, 0, BonusState.LOCKED)
                return@forEach
            }
            val account = requireNotNull(accounts[aid]) { "Unknown account" }
            val sid = event.serviceId
            if (event.type == "service_created") {
                check(sid !in services) { "Repeated service creation" }
                services[requireNotNull(sid)] = Service(sid, requireNotNull(event.name), aid,
                    requireNotNull(event.paymentTerm), requireNotNull(event.autoProlong), event.occurredAt,
                    event.occurredAt, ServiceStatus.ACTIVE, requireNotNull(event.priceMinor))
                return@forEach
            }
            // A rejected action can reference a service that was never created.
            val service = if (sid != null && event.type != "action_rejected") {
                requireNotNull(services[sid]).also { check(it.ownerId == aid) { "Service owner mismatch" } }
            } else null
            when (event.type) {
                "wallet_topped_up" -> accounts[aid] = account.copy(
                    balance = Math.addExact(account.balance, requireNotNull(event.amountMinor)),
                    totalReplenished = Math.addExact(account.totalReplenished, event.amountMinor),
                )
                "payment_succeeded" -> {
                    accounts[aid] = account.copy(balance = Math.subtractExact(account.balance, requireNotNull(event.amountMinor)))
                    services[requireNotNull(sid)] = requireNotNull(service).copy(
                        expiresAt = requireNotNull(event.paidThrough), status = ServiceStatus.ACTIVE)
                }
                "auto_prolong_changed" -> services[requireNotNull(sid)] = requireNotNull(service).copy(autoProlong = requireNotNull(event.enabled))
                "status_changed", "service_released" -> services[requireNotNull(sid)] = requireNotNull(service).copy(status = requireNotNull(event.toStatus))
                "service_restricted" -> {
                    check(event.toStatus == ServiceStatus.BLOCKED)
                    services[requireNotNull(sid)] = requireNotNull(service).copy(status = ServiceStatus.BLOCKED)
                }
                "replacement_confirmed" -> check(requireNotNull(services[event.replacementServiceId]).ownerId == aid)
                // These events are still durably stored; they do not debit the wallet.
                "payment_failed", "renewal_due", "renewal_paused", "action_rejected" -> Unit
                else -> error("Unsupported event")
            }
            if (event.type in setOf("wallet_topped_up", "payment_succeeded", "payment_failed")) {
                event.balanceBeforeMinor?.let { check(it == account.balance) { "Wallet before mismatch" } }
                event.balanceAfterMinor?.let { check(it == accounts.getValue(aid).balance) { "Wallet after mismatch" } }
            }
        }
        // Full int64 payment history is in the inbox. Legacy transactions are deliberately omitted.
        return RiskSnapshot(accounts.values.sortedBy { it.id }, services.values.sortedBy { it.id })
    }
}
