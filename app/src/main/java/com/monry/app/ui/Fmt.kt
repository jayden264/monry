package com.monry.app.ui

import com.monry.app.domain.Recurrence
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime

object Fmt {
    private val weekdays = arrayOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

    fun weekday(d: LocalDate) = weekdays[d.dayOfWeek.value - 1]

    fun monthDay(d: LocalDate) = "${d.monthValue}月${d.dayOfMonth}日"

    fun dayLabel(d: LocalDate, today: LocalDate): String = when (d) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> monthDay(d) + " " + weekday(d)
    }

    fun time(t: LocalDateTime) = "%02d:%02d".format(t.hour, t.minute)

    fun dateTime(t: LocalDateTime) = "${t.toLocalDate()} ${time(t)}"

    fun recurrence(start: LocalDateTime, r: Recurrence): String {
        val tm = time(start)
        return when (r) {
            Recurrence.ONCE -> "单次 · ${dateTime(start)}"
            Recurrence.DAILY -> "每天 $tm"
            Recurrence.WEEKLY -> "每周${weekday(start.toLocalDate()).removePrefix("周")} $tm"
            Recurrence.MONTHLY -> "每月 ${start.dayOfMonth} 日 $tm"
            Recurrence.YEARLY -> "每年 ${monthDay(start.toLocalDate())} $tm"
        }
    }

    /** Unrounded minor units -> yuan text with [scale] decimals, e.g. for the live profit ticker. */
    fun exactYuan(exactMinor: BigDecimal, scale: Int = 6): String =
        exactMinor.movePointLeft(2).setScale(scale, RoundingMode.HALF_UP).toPlainString()

    fun termText(months: Int?): String = when {
        months == null -> "无固定期限"
        months % 12 == 0 -> "${months / 12} 年"
        else -> "$months 个月"
    }
}
