package com.monry.app.domain

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

private class FakeStore(val rules: MutableList<RecurringRule>) : RecurringStore {
    /** Mirrors the unique index (ruleId, generation, occurrenceIndex). */
    val executed = mutableSetOf<Triple<Long, Int, Int>>()
    val booked = mutableListOf<Pair<Long, LocalDateTime>>()

    override suspend fun activeRules() = rules.filter { it.status == RuleStatus.ACTIVE }.toList()

    override suspend fun applyExecution(rule: RecurringRule, index: Int, scheduledAt: LocalDateTime): Boolean {
        val i = rules.indexOfFirst { it.id == rule.id }
        val cur = rules[i]
        val inserted = executed.add(Triple(rule.id, rule.generation, index))
        if (inserted) booked += rule.id to scheduledAt
        rules[i] = cur.copy(
            nextIndex = maxOf(cur.nextIndex, index + 1),
            status = if (rule.schedule.recurrence == Recurrence.ONCE) RuleStatus.COMPLETED else cur.status
        )
        return inserted
    }
}

class RecurringProcessorTest {
    private fun t(y: Int, m: Int, d: Int, h: Int = 10, min: Int = 0) = LocalDateTime.of(y, m, d, h, min)

    private fun rule(
        recurrence: Recurrence, start: LocalDateTime, id: Long = 1, status: RuleStatus = RuleStatus.ACTIVE, nextIndex: Int = 0
    ) = RecurringRule(id, "rule", 10_000, TxType.EXPENSE, 1, "其他", Schedule(start, recurrence), status, nextIndex, 0)

    @Test fun backfillsEveryMissedOccurrenceExactlyOnce() = runTest {
        val store = FakeStore(mutableListOf(rule(Recurrence.MONTHLY, t(2025, 1, 15))))
        val p = RecurringProcessor(store)
        assertEquals(4, p.run(t(2025, 4, 20)))
        assertEquals(
            listOf(t(2025, 1, 15), t(2025, 2, 15), t(2025, 3, 15), t(2025, 4, 15)),
            store.booked.map { it.second }
        )
        assertEquals(0, p.run(t(2025, 4, 20)))
        assertEquals(0, p.run(t(2025, 4, 21)))
        assertEquals(4, store.booked.size)
    }

    @Test fun nothingBookedBeforeTheScheduledTime() = runTest {
        val store = FakeStore(mutableListOf(rule(Recurrence.DAILY, t(2025, 1, 15))))
        val p = RecurringProcessor(store)
        assertEquals(0, p.run(t(2025, 1, 15, 9, 59)))
        assertEquals(1, p.run(t(2025, 1, 15, 10, 0)))
    }

    @Test fun neverDuplicatesEvenIfThePointerIsLost() = runTest {
        val store = FakeStore(mutableListOf(rule(Recurrence.MONTHLY, t(2025, 1, 15))))
        val p = RecurringProcessor(store)
        p.run(t(2025, 4, 20))
        store.rules[0] = store.rules[0].copy(nextIndex = 0) // simulate a crash that lost the pointer update
        assertEquals(0, p.run(t(2025, 4, 20)))
        assertEquals(4, store.booked.size)
    }

    @Test fun pausedAndCompletedRulesAreIgnored() = runTest {
        val store = FakeStore(
            mutableListOf(
                rule(Recurrence.DAILY, t(2025, 1, 1), id = 1, status = RuleStatus.PAUSED),
                rule(Recurrence.DAILY, t(2025, 1, 1), id = 2, status = RuleStatus.COMPLETED)
            )
        )
        assertEquals(0, RecurringProcessor(store).run(t(2025, 2, 1)))
    }

    @Test fun onceRuleRunsOnceThenCompletes() = runTest {
        val store = FakeStore(mutableListOf(rule(Recurrence.ONCE, t(2025, 1, 15))))
        val p = RecurringProcessor(store)
        assertEquals(1, p.run(t(2025, 3, 1)))
        assertEquals(RuleStatus.COMPLETED, store.rules[0].status)
        assertEquals(0, p.run(t(2025, 6, 1)))
    }

    @Test fun timeZoneChangeNeitherDuplicatesNorLosesOccurrences() = runTest {
        val store = FakeStore(mutableListOf(rule(Recurrence.DAILY, t(2025, 1, 1, 9))))
        val p = RecurringProcessor(store)
        assertEquals(3, p.run(t(2025, 1, 3, 12)))          // Jan 1, 2, 3 at 09:00
        assertEquals(0, p.run(t(2025, 1, 3, 12).minusHours(8))) // clock jumps back (travelled west)
        assertEquals(0, p.run(t(2025, 1, 3, 12)))          // and forward again
        assertEquals(1, p.run(t(2025, 1, 4, 9)))           // next day books exactly one
        assertEquals(4, store.booked.size)
    }

    @Test fun monthEndRuleBooksOnTheLastDayOfShortMonths() = runTest {
        val store = FakeStore(mutableListOf(rule(Recurrence.MONTHLY, t(2025, 1, 31))))
        RecurringProcessor(store).run(t(2025, 3, 31, 23, 0))
        assertEquals(
            listOf(t(2025, 1, 31), t(2025, 2, 28), t(2025, 3, 31)),
            store.booked.map { it.second }
        )
    }

    @Test fun largeBacklogIsDrainedInChunks() = runTest {
        val store = FakeStore(mutableListOf(rule(Recurrence.DAILY, t(2025, 1, 1))))
        val p = RecurringProcessor(store, maxPerRulePerRun = 2)
        val now = t(2025, 1, 5)
        assertEquals(2, p.run(now))
        assertEquals(2, p.run(now))
        assertEquals(1, p.run(now))
        assertEquals(0, p.run(now))
        assertEquals(5, store.booked.size)
    }
}
