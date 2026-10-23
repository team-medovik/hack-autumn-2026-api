package ru.medovik.hack2026.api.config

/**
 * Зарезервированный URI-контракт для будущей интеграции OIDC.
 *
 * TODO(OIDC): реализовать получение метаданных провайдера, PKCE, проверку подписанных state и nonce,
 * список разрешённых callback URL и правила связывания OIDC-идентичности с заведённым сотрудником.
 */
object OidcIntegrationPaths {
    const val BASE = "/api/v1/auth/oidc"
    const val LOGIN = "$BASE/{provider}/login"
    const val CALLBACK = "$BASE/{provider}/callback"
}
