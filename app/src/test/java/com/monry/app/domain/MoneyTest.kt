package com.monry.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {
    private fun ok(s: String) = (Money.parsePositive(s) as Parsed.Ok).value

    @Test fun parsesDecimalsExactly() {
        assertEquals(1250L, ok("12.5"))
        assertEquals(50L, ok(".5"))
        assertEquals(500L, ok("5."))
        assertEquals(999_999_999_999L, ok("9999999999.99"))
        assertEquals(1234500L, ok("12,345"))
    }

    @Test fun noFloatingPointDrift() {
        assertEquals(ok("0.3"), ok("0.1") + ok("0.2"))
        assertEquals(ok("1.10"), ok("0.7") + ok("0.4"))
    }

    @Test fun rejectsZeroNegativeAndGarbage() {
        assertTrue(Money.parsePositive("0") is Parsed.Err)
        assertTrue(Money.parsePositive("0.00") is Parsed.Err)
        assertTrue(Money.parsePositive("-1") is Parsed.Err)
        assertTrue(Money.parsePositive("") is Parsed.Err)
        assertTrue(Money.parsePositive("abc") is Parsed.Err)
        assertTrue(Money.parsePositive("1.234") is Parsed.Err)
        assertTrue(Money.parsePositive("12345678901") is Parsed.Err)
        assertTrue(Money.parsePositive(".") is Parsed.Err)
    }

    @Test fun nonNegativeAcceptsBlankAndZero() {
        assertEquals(0L, (Money.parseNonNegative("") as Parsed.Ok).value)
        assertEquals(0L, (Money.parseNonNegative("0") as Parsed.Ok).value)
        assertTrue(Money.parseNonNegative("-3") is Parsed.Err)
    }

    @Test fun formats() {
        assertEquals("1,234,567.89", Money.format(123_456_789))
        assertEquals("0.05", Money.format(5))
        assertEquals("-0.05", Money.format(-5))
        assertEquals("+1.00", Money.format(100, signed = true))
        assertEquals("0.00", Money.format(0, signed = true))
        assertEquals("100.00", Money.format(10_000))
    }

    @Test fun percentParsing() {
        assertEquals(0, (Percent.parse("0") as Parsed.Ok).value)
        assertEquals(325, (Percent.parse("3.25") as Parsed.Ok).value)
        assertEquals(100_000, (Percent.parse("1000") as Parsed.Ok).value)
        assertTrue(Percent.parse("1000.01") is Parsed.Err)
        assertTrue(Percent.parse("-1") is Parsed.Err)
        assertTrue(Percent.parse("") is Parsed.Err)
        assertEquals("3.25%", Percent.format(325))
    }
}
