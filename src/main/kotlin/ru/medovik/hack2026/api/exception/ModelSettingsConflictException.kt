package ru.medovik.hack2026.api.exception

import ru.medovik.hack2026.api.dto.settings.ModelSettings

class ModelSettingsConflictException(
    val current: ModelSettings,
) : RuntimeException("Настройки уже сохранили в другой версии")
