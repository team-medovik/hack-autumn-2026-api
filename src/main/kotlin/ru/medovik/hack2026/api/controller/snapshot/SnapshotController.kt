package ru.medovik.hack2026.api.controller.snapshot

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpHeaders
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.response.snapshot.SnapshotRecord
import ru.medovik.hack2026.api.dto.response.common.ApiErrorResponse
import ru.medovik.hack2026.api.dto.request.risk.RiskSnapshot
import ru.medovik.hack2026.api.service.snapshot.SnapshotService
import java.nio.charset.StandardCharsets

@RestController
@RequestMapping("/api/v1/snapshots")
@Tag(name = "Снимки", description = "История загрузок, импорт и скачивание исходных JSON-файлов биллинга")
@SecurityRequirement(name = "bearerAuth")
class SnapshotController(
    private val service: SnapshotService,
) {
    @GetMapping
    @Operation(
        operationId = "listSnapshots",
        summary = "История загрузок",
        description = "Все снимки от нового к старому. Первый снимок используется для расчётов. " +
            "Если загрузок не было, возвращается пустой массив.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "История загрузок",
            content = [Content(array = ArraySchema(schema = Schema(implementation = SnapshotRecord::class)))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "Требуется авторизация",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
    )
    fun listSnapshots(): List<SnapshotRecord> = service.list()

    @PostMapping(consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        operationId = "uploadSnapshot",
        summary = "Загрузить новый снимок",
        description = "Принимает JSON без multipart, проверяет структуру и связи, сохраняет " +
            "исходный файл и синхронно пересчитывает риск. При ошибке данные не меняются. " +
            "Максимум 20 МиБ.",
        requestBody = io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Исходный JSON-файл без multipart",
            required = true,
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                schema = Schema(implementation = RiskSnapshot::class),
                examples = [ExampleObject(
                    name = "billing",
                    summary = "Выгрузка биллинга",
                    value = """
                        {
                          "accounts": [{
                            "id": 1,
                            "email": "client@example.test",
                            "balance": 10000,
                            "withdrawBalance": 0,
                            "totalReplenished": 10000,
                            "bonusBalance": 0,
                            "bonusState": "locked"
                          }],
                          "services": [],
                          "transactions": []
                        }
                    """,
                )],
            )],
        ),
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "201",
            description = "Снимок загружен и пересчитан",
            content = [Content(schema = Schema(implementation = SnapshotRecord::class))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "Требуется авторизация",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "413",
            description = "Файл слишком большой",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "422",
            description = "Некорректный снимок",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
    )
    fun uploadSnapshot(
        @Parameter(description = "Имя загружаемого файла для истории и скачивания", example = "data.json")
        @RequestParam fileName: String,
        @RequestBody file: ByteArray,
        authentication: Authentication,
    ): ResponseEntity<SnapshotRecord> = ResponseEntity.status(HttpStatus.CREATED).body(
        service.upload(fileName = fileName, rawFile = file, uploadedBy = authentication.name)
    )

    @GetMapping("/{id}/file")
    @Operation(
        operationId = "downloadSnapshot",
        summary = "Скачать файл снимка",
        description = "Возвращает исходный JSON без изменений. Имя файла передаётся в Content-Disposition.",
    )
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Исходный JSON-файл",
            content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(type = "object"))],
        ),
        ApiResponse(
            responseCode = "401",
            description = "Требуется авторизация",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "Снимок не найден",
            content = [Content(schema = Schema(implementation = ApiErrorResponse::class))],
        ),
    )
    fun downloadSnapshot(
        @Parameter(description = "ID снимка из истории", example = "snapshot-550e8400-e29b-41d4-a716-446655440000")
        @PathVariable id: String,
    ): ResponseEntity<ByteArray> {
        val (fileName, file) = service.file(id)
        return ResponseEntity.ok()
            .header(
                HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString(),
            )
            .contentType(MediaType.APPLICATION_JSON)
            .body(file)
    }
}
