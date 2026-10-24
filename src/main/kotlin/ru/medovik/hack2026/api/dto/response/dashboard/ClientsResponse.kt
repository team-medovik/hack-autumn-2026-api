package ru.medovik.hack2026.api.dto.response.dashboard

data class ClientsResponse(
    val items: List<ClientListItem>,
    val page: Int,
    val pageSize: Int,
    val total: Int,
    val levelCounts: LevelCounts,
)
