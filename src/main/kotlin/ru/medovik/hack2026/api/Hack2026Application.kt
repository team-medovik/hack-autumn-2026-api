package ru.medovik.hack2026.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import ru.medovik.hack2026.api.properties.AuthProperties

@SpringBootApplication
@EnableConfigurationProperties(AuthProperties::class)
class Hack2026Application

fun main(args: Array<String>) {
    runApplication<Hack2026Application>(*args)
}
