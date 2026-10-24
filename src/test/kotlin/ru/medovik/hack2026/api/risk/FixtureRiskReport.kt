package ru.medovik.hack2026.api.risk

import ru.medovik.hack2026.api.risk.model.RiskSettings
import ru.medovik.hack2026.api.risk.model.RiskSnapshot
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** Demo entry point: ./gradlew riskReport, without starting Spring or a database. */
fun main(args: Array<String>) {
    require(args.size == 1) { "Expected one output path for the complete fixture risk report" }
    val mapper = jacksonObjectMapper()
    val fixture = requireNotNull(RiskEngine::class.java.getResourceAsStream("/risk/data.json")) {
        "Original hackathon fixture is missing from the test classpath"
    }.use { mapper.readValue<RiskSnapshot>(it) }
    val result = RiskEngine().calculate(
        snapshot = fixture,
        settings = RiskSettings(),
        evaluationTime = Instant.parse("2026-10-19T15:00:00Z"),
    )
    val output = Path.of(args[0]).toAbsolutePath()
    Files.createDirectories(output.parent)
    Files.writeString(output, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result) + "\n")
    println("Risk report: $output")
    println("Evaluation time: ${result.evaluationTime}; services: ${result.services.size}; customers: ${result.customers.size}")
    result.customers.sortedBy { it.accountId }.forEach { customer ->
        println("Customer ${customer.accountId}: score=${customer.attentionScore}, level=${customer.level}")
    }
}
