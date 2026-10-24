package ru.medovik.hack2026.api.repository.settings

import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import ru.medovik.hack2026.api.dto.settings.CalculationDate
import ru.medovik.hack2026.api.jooq.tables.references.CALCULATION_DATE_SETTINGS

@Repository
class CalculationDateRepository(
    private val dsl: DSLContext,
) {
    fun get(): CalculationDate = dsl.selectFrom(CALCULATION_DATE_SETTINGS)
        .where(CALCULATION_DATE_SETTINGS.ID.eq(true))
        .fetchOne()
        ?.let { CalculationDate(mode = requireNotNull(it.mode), fixedAt = it.fixedAt) }
        ?: error("Настройка даты расчёта не найдена")

    fun save(value: CalculationDate) {
        check(dsl.update(CALCULATION_DATE_SETTINGS)
            .set(CALCULATION_DATE_SETTINGS.MODE, value.mode)
            .set(CALCULATION_DATE_SETTINGS.FIXED_AT, value.fixedAt)
            .where(CALCULATION_DATE_SETTINGS.ID.eq(true))
            .execute() == 1) { "Не удалось сохранить дату расчёта" }
    }
}
