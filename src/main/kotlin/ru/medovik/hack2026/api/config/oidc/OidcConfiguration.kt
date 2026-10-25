package ru.medovik.hack2026.api.config.oidc

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.registration.ClientRegistrations
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository
import org.springframework.security.oauth2.core.AuthorizationGrantType
import ru.medovik.hack2026.api.properties.AuthProperties

@Configuration
class OidcConfiguration {

    @Bean
    fun clientRegistrationRepository(properties: AuthProperties): ClientRegistrationRepository {
        if (!properties.oidc.enabled) return ClientRegistrationRepository { null }

        val provider = requireNotNull(properties.oidc.provider?.takeIf(String::isNotBlank)) {
            "Не задан app.auth.oidc.provider"
        }
        val source = requireNotNull(properties.oidc.registrations[provider]) {
            "Не настроен OIDC-провайдер $provider"
        }
        require(source.issuerUri.isNotBlank()) { "Не задан issuer-uri для OIDC-провайдера $provider" }
        require(source.clientId.isNotBlank()) { "Не задан client-id для OIDC-провайдера $provider" }
        require(!source.clientSecret.isNullOrBlank()) {
            "Не задан client-secret для OIDC-провайдера $provider"
        }
        val registration = ClientRegistrations.fromIssuerLocation(source.issuerUri)
            .registrationId(provider)
            .clientId(source.clientId)
            .clientSecret(source.clientSecret)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}${OidcIntegrationPaths.BASE}/{registrationId}/callback")
            .scope(source.scopes)
            .build()
        return InMemoryClientRegistrationRepository(registration)
    }
}
