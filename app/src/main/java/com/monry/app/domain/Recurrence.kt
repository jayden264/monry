package com.monry.app.domain

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime

/**
 * A repeating schedule defined by its first run and a period. The n-th occurrence is always
 * computed from [start] (never from the previous occurrence), so a monthly rule started on the
 * 31st runs on 28/29 Feb, 31 Mar, 30 Apr ... and a rule started on 29 Feb runs on 28 Feb in
 * common years and 29 Feb in leap years. Times are wall-clock, so a time-zone change shifts
 * nothing and cannot create or lose an occurrence.
 */
data class Schedule(val start: LocalDateTime, val recurrence: Recurrence) {

    fun hasOccurrence(index: Int): Boolean = index >= 0 && (recurrence != Recurrence.ONCE || index == 0)

    fun occurrence(index: Int): LocalDateTime {
        require(hasOccurrence(index)) { "no occurrence $index for $recurrence" }
        val i = index.toLong()
        return when (recurrence) {
            Recurrence.ONCE -> start
            Recurrence.DAILY -> start.plusDays(i)
            Recurrence.WEEKLY -> start.plusWeeks(i)
            Recurrence.MONTHLY -> start.plusMonths(i)
            Recurrence.YEARLY -> start.plusYears(i)
        }
    }

    /** Smallest index whose occurrence is not before [now]; for ONCE returns 1 when it is already past. */
    fun firstIndexAtOrAfter(now: LocalDateTime): Int {
        if (recurrence == Recurrence.ONCE) return if (start.isBefore(now)) 1 else 0
        if (!start.isBefore(now)) return 0
        var hi = 1
        while (occurrence(hi).isBefore(now)) hi = hi shl 1
        var lo = hi shr 1 // occurrence(lo) < now (lo may be 0, which is known to be before now)
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (occurrence(mid).isBefore(now)) lo = mid else hi = mid
        }
        return hi
    }

    /** Indices >= [from] whose occurrence time has been reached by [now], at most [limit] of them. */
    fun dueIndices(from: Int, now: LocalDateTime, limit: Int): List<Int> {
        val out = ArrayList<Int>()
        var i = from
        while (out.size < limit && hasOccurrence(i) && !occurrence(i).isAfter(now)) {
            out.add(i)
            i++
        }
        return out
    }
}

data class RecurringRule(
    val id: Long,
    val name: String,
    val amount: Long,
    val type: TxType,
    val accountId: Long,
    val category: String,
    val schedule: Schedule,
    val status: RuleStatus,
    /** Next occurrence index that has not been processed yet. */
    val nextIndex: Int,
    /** Bumped whenever the schedule is changed, so old executions never collide with new ones. */
    val generation: Int
)

/** Persistence port for [RecurringProcessor]; the Room implementation lives in the data layer. */
interface RecurringStore {
    suspend fun activeRules(): List<RecurringRule>

    /**
     * Atomically records the execution (unique per rule/generation/index), books the
     * transaction and advances the rule pointer. Returns false when this occurrence had
     * already been executed (or the rule changed meanwhile) and nothing was booked.
     */
    suspend fun applyExecution(rule: RecurringRule, index: Int, scheduledAt: LocalDateTime): Boolean
}

class RecurringProcessor(
    private val store: RecurringStore,
    private val maxPerRulePerRun: Int = 5_000
) {
    private val lock = Mutex()

    /** Executes every occurrence that is due at [now], including ones missed while the app was not running. */
    suspend fun run(now: LocalDateTime): Int = lock.withLock {
        var booked = 0
        for (rule in store.activeRules()) {
            val due = rule.schedule.dueIndices(rule.nextIndex, now, maxPerRulePerRun)
            for (index in due) {
                if (store.applyExecution(rule, index, rule.schedule.occurrence(index))) booked++
            }
        }
        booked
    }
}

/** Validation for creating/editing a rule. */
object RuleValidator {
    fun validate(name: String, amount: Parsed<Long>, accountId: Long?, schedule: Schedule, now: LocalDateTime, isNew: Boolean): String? {
        if (name.isBlank()) return "请输入规则名称"
        if (name.trim().length > 30) return "名称不能超过 30 个字"
        if (amount is Parsed.Err) return amount.message
        if (accountId == null) return "请选择目标账户"
        if (schedule.recurrence == Recurrence.ONCE && schedule.start.isBefore(now) && isNew) {
            return "单次规则的执行时间必须晚于当前时间"
        }
        return null
    }
}
