package com.monry.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

/** Result of parsing/validating user input. */
sealed interface Parsed<out T> {
    data class Ok<T>(val value: T) : Parsed<T>
    data class Err(val message: String) : Parsed<Nothing>
}

val <T> Parsed<T>.valueOrNull: T? get() = (this as? Parsed.Ok)?.value
val <T> Parsed<T>.errorOrNull: String? get() = (this as? Parsed.Err)?.message

/**
 * All money is held as a [Long] number of minor units (fen). No floating point is involved
 * in storage, arithmetic or formatting; interest maths uses [BigDecimal] and is rounded once.
 */
object Money {
    /** 9,999,999,999.99 */
    const val MAX_MINOR = 999_999_999_999L

    /** Characters/shape allowed while typing an amount. */
    private val TYPING = Regex("^\\d{0,10}(\\.\\d{0,2})?$")

    fun isTypingValid(text: String) = TYPING.matches(text)

    private fun parseRaw(text: String): Parsed<Long> {
        val s = text.trim().replace(",", "")
        if (s.isEmpty()) return Parsed.Err("请输入金额")
        if (s.startsWith("-")) return Parsed.Err("金额不能为负数")
        if (!TYPING.matches(s) || s.none { it.isDigit() }) return Parsed.Err("金额格式不正确，最多两位小数")
        val intPart = s.substringBefore('.').ifEmpty { "0" }
        val frac = s.substringAfter('.', "").padEnd(2, '0')
        val minor = intPart.toLong() * 100 + frac.toLong()
        if (minor > MAX_MINOR) return Parsed.Err("金额过大")
        return Parsed.Ok(minor)
    }

    /** Strictly positive amount, for transactions, rules and investments. */
    fun parsePositive(text: String): Parsed<Long> = when (val r = parseRaw(text)) {
        is Parsed.Err -> r
        is Parsed.Ok -> if (r.value <= 0) Parsed.Err("金额必须大于 0") else r
    }

    /** Zero or more; blank input counts as zero. Used for opening balances. */
    fun parseNonNegative(text: String): Parsed<Long> =
        if (text.isBlank()) Parsed.Ok(0L) else parseRaw(text)

    fun toBigDecimal(minor: Long): BigDecimal = BigDecimal.valueOf(minor, 2)

    /** Round a value expressed in minor units to a whole number of minor units (half up). */
    fun roundMinor(exactMinor: BigDecimal): Long = exactMinor.setScale(0, RoundingMode.HALF_UP).toLong()

    /** "1,234.50"; optionally prefixed with + / -. */
    fun format(minor: Long, signed: Boolean = false): String {
        val neg = minor < 0
        val abs = if (neg) -minor else minor
        val whole = (abs / 100).toString()
        val grouped = buildString {
            whole.forEachIndexed { i, ch ->
                if (i > 0 && (whole.length - i) % 3 == 0) append(',')
                append(ch)
            }
        }
        val body = "$grouped.${(abs % 100).toString().padStart(2, '0')}"
        return when {
            neg -> "-$body"
            signed && minor > 0 -> "+$body"
            else -> body
        }
    }

    /** Plain editable text, e.g. 123450 -> "1234.5" -> "1234.50". */
    fun toInputText(minor: Long): String = toBigDecimal(minor).toPlainString()
}

object Percent {
    /** Annual rate is stored in basis points: 3.25% == 325. Max 1000.00%. */
    const val MAX_BP = 100_000

    private val TYPING = Regex("^\\d{0,4}(\\.\\d{0,2})?$")

    fun isTypingValid(text: String) = TYPING.matches(text)

    /** Zero is a legal rate. */
    fun parse(text: String): Parsed<Int> {
        val s = text.trim()
        if (s.isEmpty()) return Parsed.Err("请输入年利率")
        if (s.startsWith("-")) return Parsed.Err("年利率不能为负数")
        if (!TYPING.matches(s) || s.none { it.isDigit() }) return Parsed.Err("年利率格式不正确，最多两位小数")
        val intPart = s.substringBefore('.').ifEmpty { "0" }
        val frac = s.substringAfter('.', "").padEnd(2, '0')
        val bp = intPart.toInt() * 100 + frac.toInt()
        if (bp > MAX_BP) return Parsed.Err("年利率不能超过 1000%")
        return Parsed.Ok(bp)
    }

    fun format(bp: Int): String = BigDecimal.valueOf(bp.toLong(), 2).toPlainString() + "%"
    fun toInputText(bp: Int): String = BigDecimal.valueOf(bp.toLong(), 2).stripTrailingZeros().toPlainString()
}
