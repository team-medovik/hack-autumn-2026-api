package ru.medovik.hack2026.api.dto.response.dashboard

import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Страница сводной таблицы клиентов.")
data class ClientsResponse(
    @field:Schema(description = "Клиенты на возвращённой странице.")
    val items: List<ClientListItem>,
    @field:Schema(description = "Фактически возвращённый номер страницы, начиная с 1.", example = "1", minimum = "1")
    val page: Int,
    @field:Schema(description = "Размер страницы.", example = "12", minimum = "1", maximum = "100")
    val pageSize: Int,
    @field:Schema(description = "Общее количество клиентов, найденных по поиску или фильтру.", example = "42", minimum = "0")
    val total: Int,
    @field:Schema(description = "Количество клиентов каждого уровня во всём снимке. Не зависит от фильтра и поиска.")
    val levelCounts: LevelCounts,
)
