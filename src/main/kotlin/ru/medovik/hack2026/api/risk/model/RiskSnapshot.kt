package ru.medovik.hack2026.api.risk.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

data class RiskSnapshot(
    val accounts: List<Account>,
    val services: List<Service>,
    val transactions: List<Transaction> = emptyList(),
)

data class Account(
    val id: Long,
    val email: String,
    val balance: Long,
    val withdrawBalance: Long,
    val totalReplenished: Long,
    val bonusBalance: Long,
    val bonusState: BonusState,
) {
    init {
        require(listOf(balance, withdrawBalance, totalReplenished, bonusBalance).all { it >= 0 }) {
            "Account $id: amounts must be non-negative eurocents"
        }
    }
}

enum class BonusState {
    @JsonProperty("locked") LOCKED,
    @JsonProperty("unlocked") UNLOCKED,
}

data class Service(
    val id: Long,
    val name: String,
    val ownerId: Long,
    val paymentTerm: PaymentTerm,
    val autoProlong: Boolean,
    val createdAt: Instant,
    val expiresAt: Instant,
    val status: ServiceStatus,
    val price: Long,
) {
    init {
        require(price >= 0) { "Service $id: price must be non-negative eurocents" }
        require(!expiresAt.isBefore(createdAt)) { "Service $id: expiresAt precedes createdAt" }
    }
}

enum class PaymentTerm {
    @JsonProperty("hour") HOUR,
    @JsonProperty("week") WEEK,
    @JsonProperty("month") MONTH,
    @JsonProperty("quarter_year") QUARTER_YEAR,
    @JsonProperty("half_year") HALF_YEAR,
    @JsonProperty("year") YEAR,
}

enum class ServiceStatus(val isLive: Boolean) {
    @JsonProperty("active") ACTIVE(true),
    @JsonProperty("blocked") BLOCKED(false),
    @JsonProperty("deleted") DELETED(false),
    @JsonProperty("suspended") SUSPENDED(true),
    @JsonProperty("prolong_wait") PROLONG_WAIT(true),
}

data class Transaction(
    val id: Long,
    val amount: Int,
    val status: TransactionStatus,
    val performedAt: Instant?,
    val createdAt: Instant,
    val type: TransactionType,
    val serviceId: Long,
) {
    init {
        require(amount in 0..1000) { "Transaction $id: amount must be in 0..1000" }
        require((status == TransactionStatus.PERFORMED) == (performedAt != null)) {
            "Transaction $id: status and performedAt disagree"
        }
        require(performedAt == null || !performedAt.isBefore(createdAt)) {
            "Transaction $id: performedAt precedes createdAt"
        }
    }
}

enum class TransactionStatus {
    @JsonProperty("performed") PERFORMED,
    @JsonProperty("not_performed") NOT_PERFORMED,
}

enum class TransactionType {
    @JsonProperty("order") ORDER,
    @JsonProperty("prolong") PROLONG,
}
