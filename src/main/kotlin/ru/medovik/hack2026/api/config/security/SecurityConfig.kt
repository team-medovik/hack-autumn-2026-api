package ru.medovik.hack2026.api.config.security

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import ru.medovik.hack2026.api.config.oidc.OidcIntegrationPaths
import ru.medovik.hack2026.api.properties.AuthProperties
import ru.medovik.hack2026.api.service.auth.LocalOidcUserService

@Configuration
class SecurityConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
        requestLoggingFilter: RequestLoggingFilter,
        apiAuthenticationEntryPoint: ApiAuthenticationEntryPoint,
        apiAccessDeniedHandler: ApiAccessDeniedHandler,
        localOidcUserService: LocalOidcUserService,
        properties: AuthProperties,
    ): SecurityFilterChain {
        http.csrf {
            it.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .requireCsrfProtectionMatcher { request ->
                    request.getHeader("Authorization") == null &&
                            request.method in setOf("POST", "PUT", "PATCH", "DELETE")
                }
                .ignoringRequestMatchers(
                    "/api/v1/auth/login",
                    "/api/v1/auth/refresh",
                    "/api/v1/auth/logout",
                    "/oauth2/**",
                    "${OidcIntegrationPaths.BASE}/**",
                )
        }
        http.sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED) }
        http.authorizeHttpRequests {
            it.requestMatchers(
                "/api/v1/auth/login",
                "/api/v1/auth/refresh",
                "/api/v1/auth/logout",
                "/api/v1/auth/oidc/**",
            ).permitAll()
                .requestMatchers(
                    "/oauth2/**",
                    "/login/oauth2/**",
                    "/api/v1/docs/**",
                    "/api/v1/swagger/**",
                    "/api/v1/swagger-ui/**",
                    "/swagger-ui/**",
                    "/metrics",
                    "/actuator/health",
                    "/actuator/prometheus",
                ).permitAll()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
        }
        http.exceptionHandling {
            it.authenticationEntryPoint(apiAuthenticationEntryPoint)
                .accessDeniedHandler(apiAccessDeniedHandler)
        }
        if (properties.oidc.enabled) {
            require(properties.oidc.registrations.isNotEmpty()) { "OIDC включён, но провайдеры не настроены" }
            require(
                properties.oidc.successRedirectUri.startsWith("/") &&
                        !properties.oidc.successRedirectUri.startsWith("//")
            ) {
                "app.auth.oidc.success-redirect-uri должен быть относительным URI"
            }
            http.oauth2Login {
                it.userInfoEndpoint { endpoint -> endpoint.oidcUserService(localOidcUserService) }
                    .redirectionEndpoint { endpoint -> endpoint.baseUri("${OidcIntegrationPaths.BASE}/*/callback") }
                    .successHandler { _, response, _ -> response.sendRedirect(properties.oidc.successRedirectUri) }
            }
        }
        return http
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
            .addFilterAfter(requestLoggingFilter, JwtAuthenticationFilter::class.java)
            .build()
    }
}
