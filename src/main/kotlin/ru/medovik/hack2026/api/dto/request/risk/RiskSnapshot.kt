package ru.medovik.hack2026.api.dto.request.risk

data class RiskSnapshot(
    val accounts: List<Account>,
    val services: List<Service>,
    val transactions: List<Transaction> = emptyList(),
)
