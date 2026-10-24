package ru.medovik.hack2026.api.controller.auth

import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.servlet.view.RedirectView
import ru.medovik.hack2026.api.config.oidc.OidcIntegrationPaths

@Controller
class OidcController(
    private val registrations: ClientRegistrationRepository,
) {
    @GetMapping(OidcIntegrationPaths.LOGIN)
    fun login(@PathVariable provider: String): RedirectView {
        require(registrations.findByRegistrationId(provider) != null) { "Неизвестный OIDC-провайдер" }
        return RedirectView("/oauth2/authorization/$provider")
    }
}
