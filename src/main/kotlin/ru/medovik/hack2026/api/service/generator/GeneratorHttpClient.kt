package ru.medovik.hack2026.api.service.generator

import org.springframework.stereotype.Component
import org.springframework.http.HttpStatus
import java.time.Instant
import java.util.concurrent.TimeUnit
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

    fun simulationStatus(): SimulationStatus = control(null)

    fun setSimulationSpeed(request: SimulationSpeedRequest): SimulationStatus {
        require(request.runId.isNotBlank() && request.runId.length <= 255 && request.timeScale in SIMULATION_SPEEDS)
        return control(request)
    }

    private fun control(change: SimulationSpeedRequest?): SimulationStatus {
        fun unavailable() = SimulationControlException(HttpStatus.SERVICE_UNAVAILABLE,
            "SIMULATION_UNAVAILABLE", "Живая симуляция недоступна")
        if (!properties.enabled || properties.serviceToken.length < 16) throw unavailable()
        try {
            val path = if (change == null) "/internal/v1/generator/status" else "/internal/v1/generator/speed"
            val builder = HttpRequest.newBuilder(URI(properties.baseUrl.trimEnd('/') + path))
                .timeout(properties.requestTimeout).header("Authorization", "Bearer ${properties.serviceToken}")
            if (change == null) builder.GET() else builder.header("Content-Type", "application/json")
                .method("PATCH", HttpRequest.BodyPublishers.ofString(json.writeValueAsString(change)))
            // The future completes only after the body, so a server that sends
            // headers and then stalls cannot hold an API request indefinitely.
            val pending = http.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
            val response = try {
                pending.get(properties.requestTimeout.toMillis(), TimeUnit.MILLISECONDS)
            } catch (error: Exception) {
                pending.cancel(true)
                throw error
            }
            val bytes = when (response.statusCode()) {
                200 -> response.body()
                409 -> throw SimulationControlException(HttpStatus.CONFLICT, "SIMULATION_RUN_CHANGED",
                    "Запуск симуляции изменился; обновите состояние")
                400, 422 -> if (change != null) throw SimulationControlException(HttpStatus.BAD_REQUEST,
                    "INVALID_SIMULATION_SPEED", "Генератор отклонил параметры скорости") else throw unavailable()
                else -> throw unavailable()
            }
            check(bytes.size <= 32 * 1024)
            val root = json.readTree(bytes)
            val core = root.get("generator")
            val run = core?.get("runId")
            val time = core?.get("virtualTime")
            val finished = core?.get("finished")
            val scale = root.get("timeScale")
            val state = root.get("workerState")
            check(run?.isString == true && run.stringValue().isNotBlank() && run.stringValue().length <= 255)
            check(time?.isString == true && finished?.isBoolean == true)
            check(scale?.isNumber == true && scale.doubleValue().isFinite() && scale.doubleValue() > 0)
            check(state?.isString == true && state.stringValue() in setOf("starting", "running", "paused", "finished", "error"))
            check(root.get("syntheticOnly")?.isBoolean == true && root.get("syntheticOnly").booleanValue())
            val result = SimulationStatus(run.stringValue(), Instant.parse(time.stringValue()), scale.doubleValue(),
                state.stringValue(), finished.booleanValue())
            if (change != null) check(result.runId == change.runId && result.timeScale == change.timeScale.toDouble())
            return result
        } catch (error: SimulationControlException) {
            throw error
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            throw unavailable()
        } catch (_: Exception) {
            // Neither upstream bodies nor exception messages may reveal credentials.
            throw unavailable()
        }
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
