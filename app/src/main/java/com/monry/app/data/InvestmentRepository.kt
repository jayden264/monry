package com.monry.app.data

import androidx.room.withTransaction
import com.monry.app.domain.Categories
import com.monry.app.domain.InterestMode
import com.monry.app.domain.InvestStatus
import com.monry.app.domain.InvestTerms
import com.monry.app.domain.InvestmentCalculator
import com.monry.app.domain.Money
import com.monry.app.domain.Percent
import com.monry.app.domain.Tranche
import com.monry.app.domain.TxSource
import com.monry.app.domain.TxType
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

data class InvestDraft(
    val name: String,
    val principal: Long,
    val rateBp: Int,
    val mode: InterestMode,
    val startDate: LocalDate,
    val termMonths: Int?,
    /** Account the principal is paid from; null = already invested, do not touch any account. */
    val sourceAccountId: Long?
)

/** Everything the UI shows about one investment at one moment. */
data class InvestmentSnapshot(
    val id: Long,
    val name: String,
    val rateBp: Int,
    val mode: InterestMode,
    val termMonths: Int?,
    val status: InvestStatus,
    val startDate: LocalDate,
    val endAt: LocalDateTime?,
    val maturity: LocalDate?,
    val tranches: List<Tranche>,
    val principal: Long,
    val value: Long,
    val profit: Long,
    val valueExact: BigDecimal,
    val profitExact: BigDecimal,
    /** Profit expected at maturity, or - for open-ended investments - over the next 12 months. */
    val projectedProfit: Long,
    val projectionIsMaturity: Boolean,
    /** 0..1 progress through the term, null when there is no term. */
    val progress: Float?
) {
    val terms get() = InvestTerms(rateBp, mode, maturity)
    val isActive get() = status == InvestStatus.ACTIVE
}

fun InvestmentWithTranches.snapshot(now: LocalDateTime): InvestmentSnapshot {
    val inv = investment
    val start = LocalDate.ofEpochDay(inv.startEpochDay)
    val maturity = InvestmentCalculator.maturityDate(start, inv.termMonths)
    val terms = InvestTerms(inv.rateBp, inv.mode, maturity)
    val ts = tranches.sortedBy { it.epochDay }.map { Tranche(LocalDate.ofEpochDay(it.epochDay), it.amount) }
    val principal = ts.sumOf { it.amount }

    val value: Long
    val valueExact: BigDecimal
    val profitExact: BigDecimal
    if (inv.status == InvestStatus.ENDED) {
        value = inv.redeemedAmount ?: principal
        valueExact = BigDecimal(value)
        profitExact = valueExact.subtract(BigDecimal(principal))
    } else {
        valueExact = InvestmentCalculator.valueExact(ts, terms, now)
        value = Money.roundMinor(valueExact)
        profitExact = valueExact.subtract(BigDecimal(principal))
    }
    val projected = when {
        inv.status == InvestStatus.ENDED -> value - principal
        maturity != null -> InvestmentCalculator.profitMinor(ts, terms, maturity.atStartOfDay())
        else -> InvestmentCalculator.profitMinor(ts, terms, now.plusYears(1))
    }
    val progress = maturity?.let {
        val total = ChronoUnit.SECONDS.between(start.atStartOfDay(), it.atStartOfDay()).coerceAtLeast(1)
        (ChronoUnit.SECONDS.between(start.atStartOfDay(), now).toDouble() / total).coerceIn(0.0, 1.0).toFloat()
    }
    return InvestmentSnapshot(
        inv.id, inv.name, inv.rateBp, inv.mode, inv.termMonths, inv.status, start,
        inv.endEpochSecond?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }, maturity, ts, principal, value,
        Money.roundMinor(profitExact), valueExact, profitExact, projected, maturity != null, progress
    )
}

class InvestmentRepository(private val db: MonryDatabase) {
    private val dao = db.investments()
    private val accounts = db.accounts()
    private val txs = db.transactions()

    fun all(): Flow<List<InvestmentWithTranches>> = dao.observeAll()
    fun observe(id: Long): Flow<InvestmentWithTranches?> = dao.observe(id)

    private fun validateTerms(name: String, rateBp: Int, termMonths: Int?) {
        if (name.isBlank()) throw UserError("请输入投资对象名称")
        if (name.trim().length > 30) throw UserError("名称不能超过 30 个字")
        if (rateBp < 0 || rateBp > Percent.MAX_BP) throw UserError("年利率不合法")
        if (termMonths != null && (termMonths < 1 || termMonths > 600)) throw UserError("期限需在 1–600 个月之间")
    }

    suspend fun create(d: InvestDraft, today: LocalDate): Long = db.withTransaction {
        validateTerms(d.name, d.rateBp, d.termMonths)
        if (d.principal <= 0) throw UserError("本金必须大于 0")
        if (d.principal > Money.MAX_MINOR) throw UserError("本金过大")
        if (d.startDate.isAfter(today)) throw UserError("起始日期不能晚于今天")
        d.sourceAccountId?.let { if (accounts.get(it) == null) throw UserError("账户不存在") }

        val now = System.currentTimeMillis()
        val id = dao.insert(
            InvestmentEntity(
                name = d.name.trim(), rateBp = d.rateBp, mode = d.mode, termMonths = d.termMonths,
                status = InvestStatus.ACTIVE, startEpochDay = d.startDate.toEpochDay(), createdAt = now
            )
        )
        dao.insertTranche(TrancheEntity(investmentId = id, amount = d.principal, epochDay = d.startDate.toEpochDay()))
        d.sourceAccountId?.let { payOut(it, d.principal, d.name.trim(), d.startDate, id) }
        id
    }

    suspend fun updateTerms(id: Long, name: String, rateBp: Int, mode: InterestMode, termMonths: Int?) = db.withTransaction {
        validateTerms(name, rateBp, termMonths)
        val cur = dao.get(id)?.investment ?: throw UserError("投资不存在")
        if (cur.status != InvestStatus.ACTIVE) throw UserError("已结束的投资不能修改")
        dao.update(cur.copy(name = name.trim(), rateBp = rateBp, mode = mode, termMonths = termMonths))
    }

    suspend fun topUp(id: Long, amount: Long, date: LocalDate, sourceAccountId: Long?, today: LocalDate) = db.withTransaction {
        val cur = dao.get(id) ?: throw UserError("投资不存在")
        val inv = cur.investment
        if (inv.status != InvestStatus.ACTIVE) throw UserError("已结束的投资不能追加")
        if (amount <= 0) throw UserError("金额必须大于 0")
        if (amount > Money.MAX_MINOR) throw UserError("金额过大")
        val start = LocalDate.ofEpochDay(inv.startEpochDay)
        if (date.isBefore(start)) throw UserError("追加日期不能早于起始日期")
        if (date.isAfter(today)) throw UserError("日期不能晚于今天")
        InvestmentCalculator.maturityDate(start, inv.termMonths)?.let {
            if (!date.isBefore(it)) throw UserError("投资已到期，无法追加")
        }
        sourceAccountId?.let { if (accounts.get(it) == null) throw UserError("账户不存在") }
        dao.insertTranche(TrancheEntity(investmentId = id, amount = amount, epochDay = date.toEpochDay()))
        sourceAccountId?.let { payOut(it, amount, inv.name, date, id) }
    }

    /** Ends the investment and pays principal + accrued profit into [accountId]. Returns the amount paid. */
    suspend fun redeem(id: Long, accountId: Long, now: LocalDateTime): Long = db.withTransaction {
        val cur = dao.get(id) ?: throw UserError("投资不存在")
        if (cur.investment.status != InvestStatus.ACTIVE) throw UserError("该投资已经结束")
        if (accounts.get(accountId) == null) throw UserError("请选择到账账户")
        val snap = cur.snapshot(now)
        val amount = snap.value
        if (amount > 0) {
            txs.insert(
                TransactionEntity(
                    accountId = accountId, type = TxType.INCOME, amount = amount, category = Categories.INVEST_BACK,
                    note = "赎回：" + snap.name, date = now.toLocalDate().toEpochDay(),
                    source = TxSource.INVESTMENT, refId = id, createdAt = System.currentTimeMillis()
                )
            )
        }
        dao.update(
            cur.investment.copy(
                status = InvestStatus.ENDED, endEpochSecond = now.toEpochSecond(ZoneOffset.UTC),
                redeemedAmount = amount, redeemAccountId = accountId
            )
        )
        amount
    }

    suspend fun deleteEnded(id: Long) = db.withTransaction {
        val cur = dao.get(id) ?: return@withTransaction
        if (cur.investment.status == InvestStatus.ACTIVE) throw UserError("请先赎回或结束投资")
        dao.delete(id)
    }

    private suspend fun payOut(accountId: Long, amount: Long, name: String, date: LocalDate, investmentId: Long) {
        txs.insert(
            TransactionEntity(
                accountId = accountId, type = TxType.EXPENSE, amount = amount, category = Categories.INVEST_OUT,
                note = "投入：$name", date = date.toEpochDay(), source = TxSource.INVESTMENT,
                refId = investmentId, createdAt = System.currentTimeMillis()
            )
        )
    }
}
