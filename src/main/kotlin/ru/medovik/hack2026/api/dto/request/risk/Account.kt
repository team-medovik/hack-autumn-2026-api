package ru.medovik.hack2026.api.dto.request.risk

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
