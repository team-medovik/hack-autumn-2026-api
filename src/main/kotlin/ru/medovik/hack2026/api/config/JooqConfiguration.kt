package ru.medovik.hack2026.api.config

import org.jooq.conf.Settings
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class JooqConfiguration {

    @Bean
    fun jooqSettings(): Settings = Settings()
        .withRenderSchema(false)
}
