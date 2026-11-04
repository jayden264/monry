package com.monry.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StatsCalculatorTest {
    private fun e(type: TxType, amount: Long, cat: String, y: Int, m: Int, d: Int) =
        StatEntry(type, amount, cat, LocalDate.of(y, m, d))

    private val entries = listOf(
        e(TxType.EXPENSE, 3000, "餐饮", 2024, 2, 29),
        e(TxType.EXPENSE, 1000, "交通", 2024, 2, 29),
        e(TxType.EXPENSE, 5000, "餐饮", 2024, 2, 1),
        e(TxType.INCOME, 100_000, "工资", 2024, 2, 10),
        e(TxType.EXPENSE, 999, "餐饮", 2024, 3, 1),
        e(TxType.INCOME, 5, "红包", 2023, 12, 31)
    )

    @Test fun daySummary() {
        val s = StatsCalculator.summarize(entries, StatPeriod.DAY, LocalDate.of(2024, 2, 29))
        assertEquals(4000L, s.expense)
        assertEquals(0L, s.income)
        assertEquals(-4000L, s.net)
        assertEquals(listOf("餐饮", "交通"), s.expenseByCategory.map { it.category })
    }

    @Test fun monthSummaryInLeapFebruaryHas29DayBuckets() {
        val s = StatsCalculator.summarize(entries, StatPeriod.MONTH, LocalDate.of(2024, 2, 10))
        assertEquals(9000L, s.expense)
        assertEquals(100_000L, s.income)
        assertEquals(29, s.buckets.size)
        assertEquals(4000L, s.buckets[28].expense)
        assertEquals(5000L, s.buckets[0].expense)
    }

    @Test fun yearSummary() {
        val s = StatsCalculator.summarize(entries, StatPeriod.YEAR, LocalDate.of(2024, 6, 1))
        assertEquals(12, s.buckets.size)
        assertEquals(9999L, s.expense)
        assertEquals(100_000L, s.income)
        assertEquals(999L, s.buckets[2].expense)
    }

    @Test fun shiftingPeriods() {
        assertEquals(LocalDate.of(2024, 2, 1), StatsCalculator.shift(StatPeriod.MONTH, LocalDate.of(2024, 3, 31), -1))
        assertEquals(LocalDate.of(2025, 1, 1), StatsCalculator.shift(StatPeriod.YEAR, LocalDate.of(2024, 8, 8), 1))
        assertEquals(LocalDate.of(2024, 2, 29), StatsCalculator.shift(StatPeriod.DAY, LocalDate.of(2024, 3, 1), -1))
    }
}
