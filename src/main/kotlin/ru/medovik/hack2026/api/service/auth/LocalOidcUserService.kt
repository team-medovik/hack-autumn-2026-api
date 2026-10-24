package ru.medovik.hack2026.api.service.auth

import org.slf4j.LoggerFactory
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser
import org.springframework.security.oauth2.core.oidc.user.OidcUser
import org.springframework.stereotype.Service

@Service
class LocalOidcUserService(
    private val linking: OidcAccountLinkingService,
) : OidcUserService() {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun loadUser(userRequest: OidcUserRequest): OidcUser {
        val providerUser = super.loadUser(userRequest)
        val idToken = providerUser.idToken
        val account = linking.resolve(
            issuer = requireNotNull(idToken.issuer).toString(),
            subject = requireNotNull(idToken.subject),
            email = idToken.getClaimAsString("email") ?: providerUser.email,
            emailVerified = idToken.getClaimAsBoolean("email_verified") == true,
        )
        logger.info("OIDC login resolved: userId={}, provider={}", account.id, userRequest.clientRegistration.registrationId)
        return DefaultOidcUser(
            setOf(SimpleGrantedAuthority("ROLE_${account.role}")),
            idToken,
            providerUser.userInfo,
            "email",
        )
    }
}
