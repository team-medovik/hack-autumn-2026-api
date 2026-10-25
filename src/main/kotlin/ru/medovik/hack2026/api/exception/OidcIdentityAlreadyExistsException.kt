package ru.medovik.hack2026.api.exception

class OidcIdentityAlreadyExistsException : RuntimeException("SSO-пользователь уже привязан к другой учётной записи")
