package ru.medovik.hack2026.api.dto.response.client

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Позиция клиента в текущем отфильтрованном и отсортированном списке")
data class ClientPosition(
    @field:Schema(description = "Номер с 1; null, если клиент не прошёл фильтр", nullable = true)
    val index: Int?,
    @field:Schema(description = "Число клиентов после фильтрации")
    val total: Int,
    @field:Schema(description = "ID предыдущего клиента или null", nullable = true)
    val prevId: String?,
    @field:Schema(description = "ID следующего клиента или null", nullable = true)
    val nextId: String?,
)
