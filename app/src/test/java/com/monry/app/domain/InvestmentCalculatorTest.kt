package com.monry.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class InvestmentCalculatorTest {
    private val p = 1_000_000L // 10,000.00
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)
    private fun at(y: Int, m: Int, day: Int) = LocalDateTime.of(y, m, day, 0, 0)
    private fun simple(bp: Int = 500, maturity: LocalDate? = null) = InvestTerms(bp, InterestMode.SIMPLE, maturity)
    private fun compound(bp: Int = 500, maturity: LocalDate? = null) = InvestTerms(bp, InterestMode.COMPOUND, maturity)
    private fun value(t: InvestTerms, start: LocalDate, asOf: LocalDateTime, amount: Long = p) =
        InvestmentCalculator.valueMinor(listOf(Tranche(start, amount)), t, asOf)

    @Test fun simpleOneYear() = assertEquals(1_050_000L, value(simple(), d(2024, 1, 1), at(2025, 1, 1)))

    @Test fun simpleTwoYearsDoesNotCompound() = assertEquals(1_100_000L, value(simple(), d(2023, 1, 1), at(2025, 1, 1)))

    @Test fun compoundTwoYears() = assertEquals(1_102_500L, value(compound(), d(2023, 1, 1), at(2025, 1, 1)))

    @Test fun zeroRateKeepsPrincipal() {
        assertEquals(p, value(simple(0), d(2020, 1, 1), at(2030, 1, 1)))
        assertEquals(p, value(compound(0), d(2020, 1, 1), at(2030, 1, 1)))
    }

    @Test fun nothingAccruesBeforeOrAtStart() {
        assertEquals(p, value(simple(), d(2024, 6, 1), at(2024, 6, 1)))
        // a tranche that has not started yet is not part of the value at all
        assertEquals(0L, value(simple(), d(2024, 6, 1), at(2024, 5, 1)))
    }

    @Test fun partialYearUsesActualLengthOfLeapYear() {
        // 182 days elapsed of a 366-day year: 50,000 * 182 / 366 = 24,863.39
        assertEquals(1_024_863L, value(simple(), d(2024, 1, 1), at(2024, 7, 1)))
    }

    @Test fun compoundWithPartialYear() {
        // 1,100,000 * (1 + 0.10 * 182/366) = 1,154,699.45
        assertEquals(1_154_699L, value(compound(1000), d(2023, 1, 1), at(2024, 7, 1)))
    }

    @Test fun leapDayStartHasAnniversaryOnFeb28ThenFeb29() {
        assertEquals(1_050_000L, value(simple(), d(2024, 2, 29), at(2025, 2, 28)))
        assertEquals(1_200_000L, value(simple(), d(2024, 2, 29), at(2028, 2, 29)))
    }

    @Test fun interestStopsAtMaturity() {
        val t = simple(maturity = d(2025, 1, 1))
        assertEquals(1_050_000L, value(t, d(2024, 1, 1), at(2030, 1, 1)))
    }

    @Test fun topUpsAccrueFromTheirOwnDate() {
        val tr = listOf(Tranche(d(2024, 1, 1), 1_000_000), Tranche(d(2024, 7, 1), 500_000))
        // 1,050,000 + 500,000 + 25,000 * 184/365 (= 12,602.74)
        assertEquals(1_562_603L, InvestmentCalculator.valueMinor(tr, simple(), at(2025, 1, 1)))
        assertEquals(1_500_000L, InvestmentCalculator.principalAt(tr, at(2025, 1, 1)))
        // before the top-up only the first tranche counts
        assertEquals(1_000_000L, InvestmentCalculator.principalAt(tr, at(2024, 3, 1)))
        assertEquals(
            value(simple(), d(2024, 1, 1), at(2024, 3, 1)),
            InvestmentCalculator.valueMinor(tr, simple(), at(2024, 3, 1))
        )
    }

    @Test fun profitIsValueMinusPrincipal() {
        val tr = listOf(Tranche(d(2024, 1, 1), p))
        assertEquals(50_000L, InvestmentCalculator.profitMinor(tr, simple(), at(2025, 1, 1)))
    }

    @Test fun valueGrowsContinuouslyWithinADay() {
        val tr = listOf(Tranche(d(2024, 1, 1), p))
        val a = InvestmentCalculator.valueExact(tr, simple(), LocalDateTime.of(2024, 3, 1, 8, 0))
        val b = InvestmentCalculator.valueExact(tr, simple(), LocalDateTime.of(2024, 3, 1, 8, 0, 30))
        assertTrue(b > a)
    }

    @Test fun maturityDateClampsToMonthEnd() {
        assertEquals(d(2025, 2, 28), InvestmentCalculator.maturityDate(d(2024, 8, 31), 6))
        assertEquals(null, InvestmentCalculator.maturityDate(d(2024, 8, 31), null))
    }

    @Test fun seriesStartsAtZeroAndNeverDecreases() {
        val tr = listOf(Tranche(d(2024, 1, 1), p), Tranche(d(2024, 6, 1), p))
        val s = InvestmentCalculator.series(tr, compound(), at(2026, 1, 1), points = 30)
        assertEquals(30, s.size)
        assertEquals(0L, s.first().profitMinor)
        assertTrue(s.zipWithNext().all { (a, b) -> b.profitMinor >= a.profitMinor })
        assertEquals(InvestmentCalculator.profitMinor(tr, compound(), at(2026, 1, 1)), s.last().profitMinor)
    }
}
