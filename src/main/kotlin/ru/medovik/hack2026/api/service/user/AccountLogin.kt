package ru.medovik.hack2026.api.service.user

object AccountLogin {
    fun normalize(value: String): String = value.trim().lowercase().also { login ->
        require(login.length in 3..255) { "Логин должен содержать от 3 до 255 символов" }
    }
}
