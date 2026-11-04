package com.monry.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ScheduleTest {
    private fun t(y: Int, m: Int, d: Int, h: Int = 9, min: Int = 0) = LocalDateTime.of(y, m, d, h, min)

    @Test fun monthlyOnThe31stClampsEachMonthWithoutDrifting() {
        val s = Schedule(t(2025, 1, 31), Recurrence.MONTHLY)
        assertEquals(t(2025, 2, 28), s.occurrence(1))
        assertEquals(t(2025, 3, 31), s.occurrence(2))
        assertEquals(t(2025, 4, 30), s.occurrence(3))
        assertEquals(t(2025, 5, 31), s.occurrence(4))
    }

    @Test fun monthlyInLeapYear() {
        assertEquals(t(2024, 2, 29), Schedule(t(2024, 1, 31), Recurrence.MONTHLY).occurrence(1))
    }

    @Test fun yearlyOnLeapDay() {
        val s = Schedule(t(2024, 2, 29), Recurrence.YEARLY)
        assertEquals(t(2025, 2, 28), s.occurrence(1))
        assertEquals(t(2027, 2, 28), s.occurrence(3))
        assertEquals(t(2028, 2, 29), s.occurrence(4))
    }

    @Test fun weeklyAndDaily() {
        assertEquals(t(2025, 1, 22), Schedule(t(2025, 1, 1), Recurrence.WEEKLY).occurrence(3))
        assertEquals(t(2025, 3, 1), Schedule(t(2025, 2, 28), Recurrence.DAILY).occurrence(1))
        assertEquals(t(2024, 3, 1), Schedule(t(2024, 2, 28), Recurrence.DAILY).occurrence(2))
    }

    @Test fun onceHasExactlyOneOccurrence() {
        val s = Schedule(t(2025, 1, 1), Recurrence.ONCE)
        assertTrue(s.hasOccurrence(0))
        assertFalse(s.hasOccurrence(1))
        assertEquals(listOf(0), s.dueIndices(0, t(2030, 1, 1), 10))
        assertEquals(emptyList<Int>(), s.dueIndices(1, t(2030, 1, 1), 10))
    }

    @Test fun firstIndexAtOrAfter() {
        val s = Schedule(t(2025, 1, 31), Recurrence.MONTHLY)
        assertEquals(0, s.firstIndexAtOrAfter(t(2025, 1, 1)))
        assertEquals(0, s.firstIndexAtOrAfter(t(2025, 1, 31)))
        assertEquals(1, s.firstIndexAtOrAfter(t(2025, 1, 31, 9, 1)))
        assertEquals(1, s.firstIndexAtOrAfter(t(2025, 2, 28)))
        assertEquals(2, s.firstIndexAtOrAfter(t(2025, 3, 15)))
        assertEquals(1, Schedule(t(2025, 1, 1), Recurrence.ONCE).firstIndexAtOrAfter(t(2025, 6, 1)))
        assertEquals(0, Schedule(t(2025, 1, 1), Recurrence.ONCE).firstIndexAtOrAfter(t(2024, 6, 1)))
    }

    @Test fun firstIndexAtOrAfterAgreesWithBruteForce() {
        val s = Schedule(t(2023, 5, 31), Recurrence.MONTHLY)
        var now = t(2023, 4, 1)
        while (now.isBefore(t(2026, 1, 1))) {
            val expected = generateSequence(0) { it + 1 }.first { !s.occurrence(it).isBefore(now) }
            assertEquals(expected, s.firstIndexAtOrAfter(now))
            now = now.plusDays(7).plusHours(5)
        }
    }

    @Test fun dueIndicesRespectsNowAndLimit() {
        val s = Schedule(t(2025, 1, 31), Recurrence.MONTHLY)
        assertEquals(listOf(0, 1, 2), s.dueIndices(0, t(2025, 3, 31), 100))
        assertEquals(listOf(0, 1), s.dueIndices(0, t(2025, 3, 31), 2))
        assertEquals(listOf(2), s.dueIndices(2, t(2025, 3, 31), 100))
        assertEquals(emptyList<Int>(), s.dueIndices(0, t(2025, 1, 31, 8, 59), 100))
    }
}
