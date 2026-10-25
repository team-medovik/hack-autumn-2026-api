package ru.medovik.hack2026.api.realtime

data class EntityChangedEvent(
    val entity: RealtimeEntity,
    val id: String? = null,
)
