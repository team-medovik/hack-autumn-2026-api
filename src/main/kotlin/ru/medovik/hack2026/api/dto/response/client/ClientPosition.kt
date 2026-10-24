package ru.medovik.hack2026.api.dto.response.client

data class ClientPosition(
    val index: Int?,
    val total: Int,
    val prevId: String?,
    val nextId: String?,
)
