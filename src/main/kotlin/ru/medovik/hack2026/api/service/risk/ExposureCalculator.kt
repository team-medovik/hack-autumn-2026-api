package ru.medovik.hack2026.api.service.risk

import ru.medovik.hack2026.api.dto.request.risk.PaymentTerm
import ru.medovik.hack2026.api.dto.request.risk.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Forecast, not a billing ledger: overdue renewal restarts at evaluationTime, without past debt. */
class ExposureCalculator {
    data class Exposure(val horizonPayments: Long, val potentialMonthlyLoss: Long)

    fun calculate(service: Service, atRisk: Boolean, evaluationTime: Instant, horizonEnd: Instant): Exposure {
        require(!horizonEnd.isBefore(evaluationTime)) { "Horizon ends before evaluation time" }
        if (!atRisk || !service.status.isLive || service.expiresAt.isAfter(horizonEnd)) return Exposure(0, 0)
        val firstPayment = maxOf(service.expiresAt, evaluationTime)
        val count = paymentCount(service.paymentTerm, firstPayment, horizonEnd)
        return Exposure(Math.multiplyExact(service.price, count), monthlyLoss(service))
    }

    // Both boundaries are inclusive. Calendar payments stay anchored to the first payment
    // (Jan 31 -> Feb 28 -> Mar 31), rather than drifting after a short month.
    private fun paymentCount(term: PaymentTerm, first: Instant, end: Instant): Long {
        val seconds = Duration.between(first, end).seconds
        val periods = when (term) {
            PaymentTerm.HOUR -> seconds / 3600
            PaymentTerm.WEEK -> seconds / (7 * 86400)
            else -> {
                val monthsPerPeriod = when (term) {
                    PaymentTerm.MONTH -> 1L
                    PaymentTerm.QUARTER_YEAR -> 3L
                    PaymentTerm.HALF_YEAR -> 6L
                    PaymentTerm.YEAR -> 12L
                }
                val anchor = first.atZone(ZoneOffset.UTC)
                val finish = end.atZone(ZoneOffset.UTC)
                val monthDistance = ChronoUnit.MONTHS.between(
                    anchor.toLocalDate().withDayOfMonth(1), finish.toLocalDate().withDayOfMonth(1),
                )
                val candidate = monthDistance / monthsPerPeriod
                if (anchor.plusMonths(candidate * monthsPerPeriod).isAfter(finish)) candidate - 1 else candidate
            }
        }
        return Math.addExact(periods, 1L)
    }

    // A normalized 30-day month for hour/week; calendar tariffs are divided by their month count.
    // Round once per service, HALF_UP to integer eurocents; customer totals sum these displayed values.
    private fun monthlyLoss(service: Service): Long {
        val (numerator, denominator) = when (service.paymentTerm) {
            PaymentTerm.HOUR -> 720L to 1L
            PaymentTerm.WEEK -> 30L to 7L
            PaymentTerm.MONTH -> 1L to 1L
            PaymentTerm.QUARTER_YEAR -> 1L to 3L
            PaymentTerm.HALF_YEAR -> 1L to 6L
            PaymentTerm.YEAR -> 1L to 12L
        }
        return BigDecimal.valueOf(service.price).multiply(BigDecimal.valueOf(numerator))
            .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP).longValueExact()
    }
}
