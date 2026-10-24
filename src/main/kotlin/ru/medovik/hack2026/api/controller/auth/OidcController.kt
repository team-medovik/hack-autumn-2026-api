package ru.medovik.hack2026.api.controller.auth

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.view.RedirectView
import ru.medovik.hack2026.api.config.oidc.OidcIntegrationPaths

@Controller
@Tag(
    name = "Аутентификация",
    description = "Вход сотрудников и жизненный цикл JWT-сессии. " +
        "Учётные записи заводит оператор; публичной регистрации и подтверждения email нет.",
)
class OidcController(
    private val registrations: ClientRegistrationRepository,
) {
    @GetMapping(OidcIntegrationPaths.LOGIN)
    @Operation(
        operationId = "startOidcLogin",
        summary = "Начать вход через OIDC",
        description = "Перенаправляет браузер к настроенному провайдеру. " +
            "Дальнейший callback обрабатывает Spring Security.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "302", description = "Перенаправление к OIDC-провайдеру"),
        ApiResponse(responseCode = "400", description = "Провайдер не настроен"),
    )
    fun login(
        @Parameter(description = "Идентификатор настроенного OIDC-провайдера")
        @PathVariable provider: String,
    ): RedirectView {
        require(registrations.findByRegistrationId(provider) != null) { "Неизвестный OIDC-провайдер" }
        return RedirectView("/oauth2/authorization/$provider")
    }
}
