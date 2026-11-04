package com.monry.app.data

import androidx.room.withTransaction
import com.monry.app.domain.Money
import com.monry.app.domain.Parsed
import com.monry.app.domain.Recurrence
import com.monry.app.domain.RecurringRule
import com.monry.app.domain.RecurringStore
import com.monry.app.domain.RuleStatus
import com.monry.app.domain.RuleValidator
import com.monry.app.domain.Schedule
import com.monry.app.domain.TxSource
import com.monry.app.domain.TxType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class RuleDraft(
    val name: String,
    val amount: Parsed<Long>,
    val type: TxType,
    val accountId: Long?,
    val category: String,
    val start: LocalDateTime,
    val recurrence: Recurrence
)

fun RecurringRuleEntity.schedule(): Schedule = Schedule(
    LocalDateTime.of(LocalDate.ofEpochDay(startEpochDay), LocalTime.of(startMinuteOfDay / 60, startMinuteOfDay % 60)),
    recurrence
)

fun RecurringRuleEntity.toDomain() = RecurringRule(
    id, name, amount, type, accountId, category, schedule(), status, nextIndex, generation
)

class RecurringRepository(private val db: MonryDatabase) {
    private val dao = db.rules()

    fun rules(): Flow<List<RecurringRuleEntity>> = dao.observeAll()
    fun executions(ruleId: Long): Flow<List<RuleExecutionEntity>> = dao.observeExecutions(ruleId)
    suspend fun rule(id: Long): RecurringRuleEntity? = dao.get(id)

    /** Creates (id == null) or edits a rule. Past occurrences are never back-filled by saving. */
    suspend fun save(id: Long?, d: RuleDraft, now: LocalDateTime) = db.withTransaction {
        val schedule = Schedule(d.start.withSecond(0).withNano(0), d.recurrence)
        val cur = id?.let { dao.get(it) ?: throw UserError("规则不存在") }
        val scheduleChanged = cur == null || cur.schedule() != schedule
        RuleValidator.validate(d.name, d.amount, d.accountId, schedule, now, isNew = scheduleChanged)
            ?.let { throw UserError(it) }
        val amount = (d.amount as Parsed.Ok).value
        if (amount > Money.MAX_MINOR) throw UserError("金额过大")
        val minute = schedule.start.hour * 60 + schedule.start.minute
        val first = schedule.firstIndexAtOrAfter(now)

        if (cur == null) {
            dao.insert(
                RecurringRuleEntity(
                    name = d.name.trim(), amount = amount, type = d.type, accountId = d.accountId!!, category = d.category,
                    startEpochDay = schedule.start.toLocalDate().toEpochDay(), startMinuteOfDay = minute,
                    recurrence = d.recurrence, status = RuleStatus.ACTIVE, nextIndex = first,
                    createdAt = System.currentTimeMillis()
                )
            )
        } else {
            dao.update(
                cur.copy(
                    name = d.name.trim(), amount = amount, type = d.type, accountId = d.accountId!!, category = d.category,
                    startEpochDay = schedule.start.toLocalDate().toEpochDay(), startMinuteOfDay = minute,
                    recurrence = d.recurrence,
                    generation = if (scheduleChanged) cur.generation + 1 else cur.generation,
                    nextIndex = if (scheduleChanged) first else cur.nextIndex,
                    status = if (scheduleChanged && cur.status == RuleStatus.COMPLETED) RuleStatus.ACTIVE else cur.status
                )
            )
        }
    }

    /** Pausing keeps the pointer; resuming skips everything that fell into the paused period. */
    suspend fun setPaused(id: Long, paused: Boolean, now: LocalDateTime) = db.withTransaction {
        val r = dao.get(id) ?: throw UserError("规则不存在")
        if (r.status == RuleStatus.COMPLETED) throw UserError("该规则已执行完毕")
        if (paused) {
            dao.update(r.copy(status = RuleStatus.PAUSED))
        } else {
            val next = r.schedule().firstIndexAtOrAfter(now)
            val done = !r.schedule().hasOccurrence(next)
            dao.update(
                r.copy(
                    status = if (done) RuleStatus.COMPLETED else RuleStatus.ACTIVE,
                    nextIndex = maxOf(next, r.nextIndex)
                )
            )
        }
    }

    suspend fun delete(id: Long) = dao.delete(id)
}

/** Room-backed store used by [com.monry.app.domain.RecurringProcessor]. */
class RoomRecurringStore(private val db: MonryDatabase) : RecurringStore {
    private val rules = db.rules()
    private val txs = db.transactions()

    override suspend fun activeRules(): List<RecurringRule> = rules.active().map { it.toDomain() }

    override suspend fun applyExecution(rule: RecurringRule, index: Int, scheduledAt: LocalDateTime): Boolean =
        db.withTransaction {
            val cur = rules.get(rule.id)
            if (cur == null || cur.generation != rule.generation || cur.status != RuleStatus.ACTIVE) {
                return@withTransaction false
            }
            val now = System.currentTimeMillis()
            val execId = rules.insertExecution(
                RuleExecutionEntity(
                    ruleId = rule.id, generation = rule.generation, occurrenceIndex = index,
                    scheduledEpochDay = scheduledAt.toLocalDate().toEpochDay(),
                    scheduledMinuteOfDay = scheduledAt.hour * 60 + scheduledAt.minute,
                    executedAt = now, amount = cur.amount, type = cur.type
                )
            )
            val inserted = execId != -1L
            if (inserted) {
                val txId = txs.insert(
                    TransactionEntity(
                        accountId = cur.accountId, type = cur.type, amount = cur.amount, category = cur.category,
                        note = cur.name, date = scheduledAt.toLocalDate().toEpochDay(),
                        source = TxSource.RECURRING, refId = cur.id, createdAt = now
                    )
                )
                rules.setExecutionTx(execId, txId)
            }
            rules.advance(rule.id, rule.generation, index + 1)
            if (rule.schedule.recurrence == Recurrence.ONCE) rules.complete(rule.id, rule.generation)
            inserted
        }
}
