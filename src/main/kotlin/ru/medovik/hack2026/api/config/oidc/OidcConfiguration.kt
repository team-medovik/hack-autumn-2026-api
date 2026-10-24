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

        require(properties.oidc.registrations.isNotEmpty()) { "OIDC включён, но провайдеры не настроены" }
        val registrations = properties.oidc.registrations.map { (registrationId, source) ->
            require(source.clientId.isNotBlank()) { "Не задан client-id для OIDC-провайдера $registrationId" }
            require(!source.clientSecret.isNullOrBlank()) {
                "Не задан client-secret для OIDC-провайдера $registrationId"
            }
            ClientRegistrations.fromIssuerLocation(source.issuerUri)
                .registrationId(registrationId)
                .clientId(source.clientId)
                .clientSecret(source.clientSecret)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}${OidcIntegrationPaths.BASE}/{registrationId}/callback")
                .scope(source.scopes)
                .build()
        }
        return InMemoryClientRegistrationRepository(registrations)
    }
}
