package ru.medovik.hack2026.api.config

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.KotlinModule

@Configuration
class JacksonConfig {

    @Bean
    fun jsonMapperBuilderCustomizer(): JsonMapperBuilderCustomizer = JsonMapperBuilderCustomizer { builder ->
        builder.addModule(
            KotlinModule.Builder()
                .disable(KotlinFeature.StrictNullChecks)
                .build(),
        )
    }
}
