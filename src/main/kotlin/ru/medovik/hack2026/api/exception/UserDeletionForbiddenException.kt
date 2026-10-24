package ru.medovik.hack2026.api.exception

class UserDeletionForbiddenException(
    val code: String,
    message: String,
) : RuntimeException(message)
