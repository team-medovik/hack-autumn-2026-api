package ru.medovik.hack2026.api.controller.contract

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.medovik.hack2026.api.dto.response.snapshot.SnapshotRecord
import ru.medovik.hack2026.api.service.snapshot.SnapshotService

@RestController
@Tag(name = "Источник данных")
@SecurityRequirement(name = "bearerAuth")
class ContractSourceController(private val snapshots: SnapshotService) {
    /**
     * Full, atomic replacement. Uses the already validated source upload and database
     * transaction from the legacy POST /api/v1/snapshots.
     */
    @PutMapping("/api/v1/source/snapshot", consumes = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(operationId = "replaceSourceSnapshot", summary = "Заменить исходный снимок и пересчитать риски")
    fun replace(
        @RequestParam(required = false, defaultValue = "snapshot.json") fileName: String,
        @RequestBody body: ByteArray,
        authentication: Authentication,
    ): SnapshotRecord = snapshots.upload(fileName = fileName, rawFile = body, uploadedBy = authentication.name)
}
