package ru.medovik.hack2026.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling
import ru.medovik.hack2026.api.properties.AuthProperties
import ru.medovik.hack2026.api.properties.GeneratorProperties
import ru.medovik.hack2026.api.properties.MlProperties

@SpringBootApplication
@EnableConfigurationProperties(AuthProperties::class, GeneratorProperties::class, MlProperties::class)
@EnableScheduling
class Hack2026Application

fun main(args: Array<String>) {
    runApplication<Hack2026Application>(*args)
}
