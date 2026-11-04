package com.monry.app.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** One deposit into an investment. The first tranche is the initial principal; later ones are top-ups. */
data class Tranche(val date: LocalDate, val amount: Long)

data class InvestTerms(
    val rateBp: Int,
    val mode: InterestMode,
    /** Interest stops accruing at the start of this day. Null = open ended. */
    val maturity: LocalDate? = null
)

data class SeriesPoint(val epochSecond: Long, val profitMinor: Long)

/**
 * Interest model (wall-clock time, no time zones involved):
 *  - A tranche accrues from 00:00 of its date.
 *  - Elapsed time is split into whole anniversaries n (calendar years, 29 Feb falls back to 28 Feb)
 *    and a fraction f of the current anniversary-year, measured in seconds (so leap years are exact).
 *  - Simple:   P * (1 + r * (n + f))
 *  - Compound: P * (1 + r)^n * (1 + r * f)
 * Everything is BigDecimal; callers round once at the end via [Money.roundMinor].
 */
object InvestmentCalculator {
    private val MC = MathContext(50, RoundingMode.HALF_EVEN)
    private val BP = BigDecimal(10_000)

    fun maturityDate(start: LocalDate, termMonths: Int?): LocalDate? =
        termMonths?.let { start.plusMonths(it.toLong()) }

    private fun effectiveEnd(asOf: LocalDateTime, terms: InvestTerms): LocalDateTime {
        val m = terms.maturity?.atStartOfDay() ?: return asOf
        return if (asOf.isAfter(m)) m else asOf
    }

    /** Value of one tranche in minor units, unrounded. */
    fun trancheValueExact(principal: Long, terms: InvestTerms, start: LocalDate, asOf: LocalDateTime): BigDecimal {
        val p = BigDecimal(principal)
        val s = start.atStartOfDay()
        val end = effectiveEnd(asOf, terms)
        if (!end.isAfter(s) || terms.rateBp == 0) return p
        val rate = BigDecimal(terms.rateBp).divide(BP, MC)

        var n = 0
        while (!s.plusYears(n + 1L).isAfter(end)) n++
        val a = s.plusYears(n.toLong())
        val b = s.plusYears(n + 1L)
        val frac = BigDecimal(ChronoUnit.SECONDS.between(a, end))
            .divide(BigDecimal(ChronoUnit.SECONDS.between(a, b)), MC)

        return when (terms.mode) {
            InterestMode.SIMPLE ->
                p.multiply(BigDecimal.ONE.add(rate.multiply(BigDecimal(n).add(frac), MC), MC), MC)
            InterestMode.COMPOUND ->
                p.multiply(BigDecimal.ONE.add(rate).pow(n, MC), MC)
                    .multiply(BigDecimal.ONE.add(rate.multiply(frac, MC), MC), MC)
        }
    }

    /** Sum of tranche values (only tranches already started at [asOf]), minor units, unrounded. */
    fun valueExact(tranches: List<Tranche>, terms: InvestTerms, asOf: LocalDateTime): BigDecimal =
        tranches
            .filter { !it.date.atStartOfDay().isAfter(asOf) }
            .fold(BigDecimal.ZERO) { acc, t -> acc.add(trancheValueExact(t.amount, terms, t.date, asOf)) }

    fun valueMinor(tranches: List<Tranche>, terms: InvestTerms, asOf: LocalDateTime): Long =
        Money.roundMinor(valueExact(tranches, terms, asOf))

    fun principalAt(tranches: List<Tranche>, asOf: LocalDateTime): Long =
        tranches.filter { !it.date.atStartOfDay().isAfter(asOf) }.sumOf { it.amount }

    fun profitExact(tranches: List<Tranche>, terms: InvestTerms, asOf: LocalDateTime): BigDecimal =
        valueExact(tranches, terms, asOf).subtract(BigDecimal(principalAt(tranches, asOf)))

    fun profitMinor(tranches: List<Tranche>, terms: InvestTerms, asOf: LocalDateTime): Long =
        Money.roundMinor(profitExact(tranches, terms, asOf))

    /** Cumulative profit sampled evenly from the first deposit to [to]. */
    fun series(tranches: List<Tranche>, terms: InvestTerms, to: LocalDateTime, points: Int = 48): List<SeriesPoint> {
        if (tranches.isEmpty()) return emptyList()
        val from = tranches.minOf { it.date }.atStartOfDay()
        val total = ChronoUnit.SECONDS.between(from, to).coerceAtLeast(0)
        val n = if (total == 0L) 1 else points.coerceAtLeast(2)
        return (0 until n).map { i ->
            val at = from.plusSeconds(if (n == 1) 0 else total * i / (n - 1))
            SeriesPoint(at.toEpochSecond(ZoneOffset.UTC), profitMinor(tranches, terms, at))
        }
    }
}
