package ru.medovik.hack2026.api.repository.settings

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import ru.medovik.hack2026.api.dto.settings.ModelSettingsValues
import ru.medovik.hack2026.api.jooq.tables.references.MODEL_SETTINGS
import tools.jackson.databind.json.JsonMapper
import java.time.OffsetDateTime

@Repository
class ModelSettingsRepository(
    private val dsl: DSLContext,
    private val jsonMapper: JsonMapper,
) {
    fun current(): StoredSettings = dsl.selectFrom(MODEL_SETTINGS)
        .orderBy(MODEL_SETTINGS.VERSION.desc())
        .limit(1)
        .fetchOne()
        ?.let { record ->
            StoredSettings(
                version = requireNotNull(record.version),
                updatedAt = requireNotNull(record.updatedAt),
                updatedBy = requireNotNull(record.updatedBy),
                values = jsonMapper.readValue(requireNotNull(record.payload).data(), ModelSettingsValues::class.java),
            )
        }
        ?: error("Начальная версия настроек модели не найдена")

    fun lockForSave() {
        dsl.fetch("SELECT pg_advisory_xact_lock(20261024)")
    }

    fun insert(version: Long, updatedAt: OffsetDateTime, updatedBy: String, values: ModelSettingsValues) {
        dsl.execute(
            "INSERT INTO model_settings (version, updated_at, updated_by, payload) VALUES (?, CAST(? AS timestamptz), ?, CAST(? AS jsonb))",
            version,
            updatedAt,
            updatedBy,
            jsonMapper.writeValueAsString(values),
        )
    }

    data class StoredSettings(
        val version: Long,
        val updatedAt: OffsetDateTime,
        val updatedBy: String,
        val values: ModelSettingsValues,
    )
}
