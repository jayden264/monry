package com.monry.app.domain

import java.time.LocalDate
import java.time.YearMonth

enum class StatPeriod(val label: String) { DAY("日"), MONTH("月"), YEAR("年") }

data class StatEntry(val type: TxType, val amount: Long, val category: String, val date: LocalDate)

data class Bucket(val label: String, val income: Long, val expense: Long)
data class CategoryShare(val category: String, val amount: Long)

data class StatSummary(
    val from: LocalDate,
    val to: LocalDate,
    val income: Long,
    val expense: Long,
    val buckets: List<Bucket>,
    val expenseByCategory: List<CategoryShare>,
    val incomeByCategory: List<CategoryShare>
) {
    val net: Long get() = income - expense
}

object StatsCalculator {
    fun range(period: StatPeriod, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (period) {
        StatPeriod.DAY -> anchor to anchor
        StatPeriod.MONTH -> YearMonth.from(anchor).let { it.atDay(1) to it.atEndOfMonth() }
        StatPeriod.YEAR -> LocalDate.of(anchor.year, 1, 1) to LocalDate.of(anchor.year, 12, 31)
    }

    fun shift(period: StatPeriod, anchor: LocalDate, delta: Int): LocalDate = when (period) {
        StatPeriod.DAY -> anchor.plusDays(delta.toLong())
        StatPeriod.MONTH -> anchor.withDayOfMonth(1).plusMonths(delta.toLong())
        StatPeriod.YEAR -> anchor.withDayOfMonth(1).withMonth(1).plusYears(delta.toLong())
    }

    fun summarize(entries: List<StatEntry>, period: StatPeriod, anchor: LocalDate): StatSummary {
        val (from, to) = range(period, anchor)
        val inRange = entries.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
        val income = inRange.filter { it.type == TxType.INCOME }.sumOf { it.amount }
        val expense = inRange.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }

        val buckets = when (period) {
            StatPeriod.DAY -> emptyList()
            StatPeriod.MONTH -> (1..to.dayOfMonth).map { d ->
                val day = from.withDayOfMonth(d)
                val es = inRange.filter { it.date == day }
                Bucket(d.toString(), es.sumOfType(TxType.INCOME), es.sumOfType(TxType.EXPENSE))
            }
            StatPeriod.YEAR -> (1..12).map { m ->
                val es = inRange.filter { it.date.monthValue == m }
                Bucket(m.toString(), es.sumOfType(TxType.INCOME), es.sumOfType(TxType.EXPENSE))
            }
        }
        return StatSummary(from, to, income, expense, buckets, shares(inRange, TxType.EXPENSE), shares(inRange, TxType.INCOME))
    }

    private fun List<StatEntry>.sumOfType(type: TxType) = filter { it.type == type }.sumOf { it.amount }

    private fun shares(entries: List<StatEntry>, type: TxType) =
        entries.filter { it.type == type }
            .groupBy { it.category }
            .map { (c, l) -> CategoryShare(c, l.sumOf { it.amount }) }
            .sortedByDescending { it.amount }
}
