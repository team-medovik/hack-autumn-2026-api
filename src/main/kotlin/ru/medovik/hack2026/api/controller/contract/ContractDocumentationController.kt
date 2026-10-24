package ru.medovik.hack2026.api.controller.contract

import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.yaml.snakeyaml.Yaml

/**
 * Returns the real OpenAPI 3.1 document as JSON, exactly as specified in unified.yaml.
 * Swagger UI and API consumers read the same bundled source of truth.
 */
@RestController
class ContractDocumentationController {
    private val document: Map<String, Any?> by lazy {
        val resource = ClassPathResource("openapi/unified.yaml")
        resource.inputStream.use { stream ->
            Yaml().load<Map<String, Any?>>(stream)
                ?: error("Bundled unified OpenAPI document is empty")
        }
    }

    @GetMapping("/api/v1/openapi", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun contract(): Map<String, Any?> = document
}
