package ru.medovik.hack2026.api.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType
import io.swagger.v3.oas.annotations.info.Info
import io.swagger.v3.oas.annotations.security.SecurityScheme

@OpenAPIDefinition(
    info = Info(
        title = "API Hack Autumn 2026",
        version = "v1",
        description = "API аутентификации сотрудников, сводки рисков и таблицы клиентов.",
    ),
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    `in` = SecuritySchemeIn.HEADER,
)
class OpenApiConfig
