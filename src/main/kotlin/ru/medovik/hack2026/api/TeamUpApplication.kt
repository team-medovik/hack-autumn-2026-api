package ru.medovik.hack2026.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import ru.medovik.hack2026.api.properties.AuthProperties

@SpringBootApplication
@EnableConfigurationProperties(AuthProperties::class)
class TeamUpApplication

fun main(args: Array<String>) {
    runApplication<TeamUpApplication>(*args)
}
