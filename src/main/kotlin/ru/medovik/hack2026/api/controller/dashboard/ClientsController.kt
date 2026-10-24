package ru.medovik.hack2026.api.controller.dashboard

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.response.dashboard.ClientsResponse
import ru.medovik.hack2026.api.dto.response.client.ClientCard
import ru.medovik.hack2026.api.dto.response.client.ClientPosition
import ru.medovik.hack2026.api.dto.response.common.ApiErrorResponse
import ru.medovik.hack2026.api.service.dashboard.DashboardService

@RestController
@RequestMapping("/api/v1/clients")
@Tag(
    name = "Клиенты",
    description = "Сводная таблица клиентов последнего снимка с серверными поиском, фильтрацией и пагинацией."
)
@SecurityRequirement(name = "bearerAuth")
class ClientsController(
    private val dashboardService: DashboardService,
) {
    @GetMapping
    @Operation(
        summary = "Получить страницу сводной таблицы клиентов",
        description = """
            Возвращает клиентов из последнего снимка биллинга. Поиск выполняется по подстроке
            email или ID без учёта регистра и отменяет фильтрацию по \`level\`.

            При сортировке по \`score\` клиенты без оценки всегда находятся в конце, независимо
            от направления сортировки. Если запрошенная страница больше последней, возвращается
            последняя существующая страница. Без снимков ответ успешный и содержит пустой список.
        """,
        operationId = "listClients",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Страница клиентов получена, в том числе при отсутствии снимков",
            content = [Content(schema = Schema(implementation = ClientsResponse::class))],
        ),
        ApiResponse(
            responseCode = "400",
            description = "Некорректный уровень риска, сортировка, направление или параметры пагинации",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "Access-токен отсутствует, истёк или некорректен",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
    )
    fun listClients(
        @Parameter(
            description = "Фильтр по уровню риска. Без параметра возвращаются клиенты всех уровней.",
            schema = Schema(allowableValues = ["high", "medium", "low", "none"]),
        )
        @RequestParam(required = false) level: String?,
        @Parameter(
            description = "Подстрока email или ID без учёта регистра. При поиске фильтр level не применяется.",
            example = "client5",
        )
        @RequestParam(required = false) search: String?,
        @Parameter(
            description = "Поле сортировки.",
            schema = Schema(
                allowableValues = ["score", "id", "email", "balance", "topUp", "monthlyLoss"],
                defaultValue = "score",
            ),
        )
        @RequestParam(required = false) sort: String?,
        @Parameter(
            description = "Направление сортировки. Для score строки без оценки всегда остаются последними.",
            schema = Schema(allowableValues = ["asc", "desc"], defaultValue = "desc"),
        )
        @RequestParam(required = false) order: String?,
        @Parameter(
            description = "Номер страницы, начиная с 1. Страница за пределами диапазона заменяется последней.",
            schema = Schema(minimum = "1", defaultValue = "1"),
        )
        @RequestParam(required = false) page: String?,
        @Parameter(
            description = "Количество строк на странице.",
            schema = Schema(minimum = "1", maximum = "100", defaultValue = "12"),
        )
        @RequestParam(required = false) pageSize: String?,
    ): ClientsResponse = dashboardService.listClients(
        level = level,
        search = search,
        sort = sort,
        order = order,
        page = page,
        pageSize = pageSize,
    )

    @GetMapping("/{id}")
    @Operation(
        operationId = "getClientCard",
        summary = "Карточка клиента",
        description = "Показывает score, причины риска, баланс, услуги и расчёт ежемесячных " +
            "потерь по активному снимку. Клиент без оценки возвращается с null в score.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Карточка получена",
            content = [Content(schema = Schema(implementation = ClientCard::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "Требуется авторизация",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "Клиент не найден",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
    )
    fun getClientCard(
        @Parameter(description = "ID клиента из списка", example = "1") @PathVariable id: String,
    ): ClientCard = dashboardService.getClientCard(id = id)

    @GetMapping("/{id}/position")
    @Operation(
        operationId = "getClientPosition",
        summary = "Место клиента в списке и его соседи",
        description = "Применяет те же поиск, фильтр и сортировку, что список клиентов. " +
            "Если клиент исключён фильтром, index, prevId и nextId равны null.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Позиция получена",
            content = [Content(schema = Schema(implementation = ClientPosition::class))],
        ),
        ApiResponse(
            responseCode = "400",
            description = "Некорректные параметры списка",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "Требуется авторизация",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "Клиент не найден",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
    )
    fun getClientPosition(
        @Parameter(description = "ID клиента из списка", example = "1") @PathVariable id: String,
        @Parameter(
            description = "Уровень риска. При поиске фильтр не применяется.",
            schema = Schema(allowableValues = ["high", "medium", "low", "none"]),
        )
        @RequestParam(required = false) level: String?,
        @Parameter(description = "Подстрока email или ID без учёта регистра")
        @RequestParam(required = false) search: String?,
        @Parameter(
            description = "Поле сортировки",
            schema = Schema(
                allowableValues = ["score", "id", "email", "balance", "topUp", "monthlyLoss"],
                defaultValue = "score",
            ),
        )
        @RequestParam(required = false) sort: String?,
        @Parameter(
            description = "Направление сортировки",
            schema = Schema(allowableValues = ["asc", "desc"], defaultValue = "desc"),
        )
        @RequestParam(required = false) order: String?,
    ): ClientPosition = dashboardService.getClientPosition(
        id = id,
        level = level,
        search = search,
        sort = sort,
        order = order,
    )
}
