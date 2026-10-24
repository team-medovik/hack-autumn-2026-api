package ru.medovik.hack2026.api.exception

class WeakPasswordException : RuntimeException("Пароль должен содержать от 8 до 255 символов, строчную и заглавную буквы и цифру")
