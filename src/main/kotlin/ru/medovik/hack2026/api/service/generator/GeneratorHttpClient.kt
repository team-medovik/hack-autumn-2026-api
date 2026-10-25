package ru.medovik.hack2026.api.service.generator

import org.springframework.stereotype.Component
import ru.medovik.hack2026.api.properties.GeneratorProperties
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets

interface GeneratorSource {
    fun runId(): String
    fun page(runId: String, after: Long): GeneratorPage
}

@Component
class GeneratorHttpClient(private val properties: GeneratorProperties, mapper: JsonMapper) : GeneratorSource {
    private val json = mapper.rebuild().disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS).enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()
    private val http = HttpClient.newBuilder().connectTimeout(properties.requestTimeout)
        .followRedirects(HttpClient.Redirect.NEVER).build()

    override fun runId(): String = get("/internal/v1/generator/status", GeneratorStatus::class.java).generator.runId

    override fun page(runId: String, after: Long): GeneratorPage {
        val run = URLEncoder.encode(runId, StandardCharsets.UTF_8)
        return get("/internal/v1/generator/events?runId=$run&afterSequence=$after&limit=${properties.pageSize}", GeneratorPage::class.java)
            .also { require(it.runId == runId); it.validate(after) }
    }

    private fun <T> get(path: String, type: Class<T>): T {
        val request = HttpRequest.newBuilder(URI(properties.baseUrl.trimEnd('/') + path))
            .timeout(properties.requestTimeout).header("Authorization", "Bearer ${properties.serviceToken}").GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
        check(response.statusCode() == 200) { "Generator HTTP ${response.statusCode()}" }
        require(response.body().size <= 8 * 1024 * 1024) { "Generator response too large" }
        return json.readValue(response.body(), type)
    }
}
