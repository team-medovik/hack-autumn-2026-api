package ru.medovik.hack2026.api.controller.contract

import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Expose the same unified contract as Swagger UI instead of generating a divergent one
 * from Spring annotations. Automatic SpringDoc JSON remains at /api/v1/internal-openapi.
 */
@RestController
class ContractDocumentationController {
    private val specification: String by lazy {
        ClassPathResource("openapi/unified.yaml").inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    @GetMapping("/api/v1/openapi", produces = ["application/yaml"])
    fun contract(): ResponseEntity<String> = ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("application/yaml"))
        .body(specification)
}
