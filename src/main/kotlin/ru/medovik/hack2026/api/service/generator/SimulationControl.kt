package ru.medovik.hack2026.api.service.generator

import org.springframework.http.HttpStatus
import tools.jackson.databind.JsonNode
import java.time.Instant

val SIMULATION_SPEEDS = listOf(1, 10, 60)

data class SimulationSpeedRequest(val runId: String, val timeScale: Int) {
    companion object {
        fun parse(body: JsonNode): SimulationSpeedRequest {
            val run = body.get("runId")
            val speed = body.get("timeScale")
            if (!body.isObject || body.size() != 2 || run?.isString != true ||
                run.stringValue().isBlank() || run.stringValue().length > 255 ||
                speed?.isIntegralNumber != true || !speed.canConvertToInt() ||
                speed.intValue() !in SIMULATION_SPEEDS
            ) throw SimulationControlException(HttpStatus.BAD_REQUEST, "INVALID_SIMULATION_SPEED",
                "Нужны runId и целая скорость 1, 10 или 60")
            return SimulationSpeedRequest(run.stringValue(), speed.intValue())
        }
    }
}

data class SimulationStatus(
    val runId: String,
    val virtualTime: Instant,
    val timeScale: Double,
    val workerState: String,
    val finished: Boolean,
    val syntheticOnly: Boolean = true,
    val supportedTimeScales: List<Int> = SIMULATION_SPEEDS,
)

class SimulationControlException(val status: HttpStatus, val code: String, message: String) : RuntimeException(message)
