package ru.medovik.hack2026.api.risk

import ru.medovik.hack2026.api.risk.model.RiskResult
import ru.medovik.hack2026.api.risk.model.RiskSettings
import ru.medovik.hack2026.api.risk.model.RiskSnapshot
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/** Запуск через задачу Gradle riskReport без Spring и подключения к базе. */
fun main(args: Array<String>) {
    val result = runRiskReport(args)
    println("Время расчёта: ${result.evaluationTime}; услуг: ${result.services.size}; клиентов: ${result.customers.size}")
    result.customers.forEach { customer ->
        println("Клиент ${customer.accountId}: score=${customer.attentionScore}, уровень=${customer.level}")
    }
}

fun runRiskReport(args: Array<String>): RiskResult {
    val options = parseOptions(args)
    val mapper = jacksonObjectMapper()
    val input = options["--input"]?.let { Path.of(it).toAbsolutePath().normalize() }
    val settingsPath = options["--settings"]?.let { Path.of(it).toAbsolutePath().normalize() }
    val output = Path.of(options.getValue("--output")).toAbsolutePath().normalize()
    require(input == null || "--at" in options) {
        "Для внешнего JSON укажите время snapshot через --at / -PriskAt (ISO 8601 с часовым поясом)"
    }
    val evaluationTime = Instant.parse(options["--at"] ?: "2026-10-19T15:00:00Z")
    val bundledFixture = RiskEngine::class.java.getResource("/risk/data.json")
    val protectedPaths = listOfNotNull(
        input,
        settingsPath,
        bundledFixture?.takeIf { it.protocol == "file" }?.let { Path.of(it.toURI()) },
        Path.of("src/test/resources/risk/data.json").toAbsolutePath().normalize(),
    )
    require(protectedPaths.none { samePath(output, it) }) {
        "Путь отчёта не должен совпадать с исходными данными или файлом настроек"
    }
    val snapshot = if (input != null) {
        Files.newInputStream(input).use { mapper.readValue<RiskSnapshot>(it) }
    } else {
        requireNotNull(bundledFixture) { "Исходный fixture /risk/data.json отсутствует в classpath" }
            .openStream().use { mapper.readValue<RiskSnapshot>(it) }
    }
    val settings = if (settingsPath == null) RiskSettings() else {
        Files.newInputStream(settingsPath).use { mapper.readValue<RiskSettings>(it) }
    }
    val result = RiskEngine().calculate(
        snapshot = snapshot,
        settings = settings,
        evaluationTime = evaluationTime,
    )
    val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result) + "\n"
    Files.createDirectories(output.parent)
    Files.writeString(output, json)
    println("Отчёт: $output")
    return result
}

private fun parseOptions(args: Array<String>): Map<String, String> {
    val allowed = setOf("--input", "--output", "--at", "--settings")
    require(args.size % 2 == 0) { "Аргументы задаются парами: --параметр значение" }
    val options = linkedMapOf<String, String>()
    args.toList().chunked(2).forEach { (option, value) ->
        require(option in allowed) { "Неизвестный параметр: $option" }
        require(option !in options) { "Повторный параметр: $option" }
        require(value.isNotBlank() && !value.startsWith("--")) { "Не задано значение $option" }
        options[option] = value
    }
    require("--output" in options) { "Укажите путь отчёта через --output" }
    return options
}

private fun samePath(first: Path, second: Path): Boolean =
    first.toAbsolutePath().normalize() == second.toAbsolutePath().normalize() ||
        (Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second))
