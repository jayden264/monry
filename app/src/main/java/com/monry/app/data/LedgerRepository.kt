package com.monry.app.data

import androidx.room.withTransaction
import com.monry.app.domain.AccountType
import com.monry.app.domain.Money
import com.monry.app.domain.TxSource
import com.monry.app.domain.TxType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** An error whose message is safe to show to the user as-is. */
class UserError(message: String) : Exception(message)

class LedgerRepository(private val db: MonryDatabase) {
    private val accountDao = db.accounts()
    private val txDao = db.transactions()

    val accounts: Flow<List<AccountWithBalance>> = accountDao.observeWithBalance()

    fun recent(limit: Int): Flow<List<TransactionEntity>> = txDao.observeRecent(limit)

    fun range(from: LocalDate, to: LocalDate): Flow<List<TransactionEntity>> =
        txDao.observeRange(from.toEpochDay(), to.toEpochDay())

    suspend fun transaction(id: Long): TransactionEntity? = txDao.get(id)

    // ---- accounts -------------------------------------------------------------------------

    suspend fun createAccount(name: String, type: AccountType, opening: Long): Long {
        checkName(name)
        if (opening < 0 || opening > Money.MAX_MINOR) throw UserError("初始金额不合法")
        return accountDao.insert(
            AccountEntity(name = name.trim(), type = type, openingBalance = opening, createdAt = System.currentTimeMillis())
        )
    }

    suspend fun createAccounts(items: List<Triple<String, AccountType, Long>>) = db.withTransaction {
        items.forEach { (n, t, o) -> createAccount(n, t, o) }
    }

    suspend fun updateAccount(id: Long, name: String, type: AccountType) {
        checkName(name)
        val a = accountDao.get(id) ?: throw UserError("账户不存在")
        accountDao.update(a.copy(name = name.trim(), type = type))
    }

    /** Sets the current balance by shifting the opening balance, so the ledger stays untouched. */
    suspend fun setBalance(id: Long, target: Long) = db.withTransaction {
        if (target < -Money.MAX_MINOR || target > Money.MAX_MINOR) throw UserError("金额不合法")
        val a = accountDao.get(id) ?: throw UserError("账户不存在")
        val current = accountDao.balanceOf(id) ?: 0L
        accountDao.update(a.copy(openingBalance = a.openingBalance + (target - current)))
    }

    suspend fun deleteAccount(id: Long) {
        try {
            accountDao.delete(id)
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            throw UserError("该账户仍有流水或定时规则，无法删除")
        }
    }

    private fun checkName(name: String) {
        if (name.isBlank()) throw UserError("请输入账户名称")
        if (name.trim().length > 20) throw UserError("账户名称不能超过 20 个字")
    }

    // ---- transactions ---------------------------------------------------------------------

    suspend fun saveTransaction(
        id: Long?, accountId: Long?, type: TxType, amount: Long, category: String,
        note: String, date: LocalDate, today: LocalDate
    ) = db.withTransaction {
        if (accountId == null || accountDao.get(accountId) == null) throw UserError("请选择账户")
        if (amount <= 0) throw UserError("金额必须大于 0")
        if (amount > Money.MAX_MINOR) throw UserError("金额过大")
        if (date.isAfter(today)) throw UserError("日期不能晚于今天")
        if (category.isBlank()) throw UserError("请选择分类")
        val cleanNote = note.trim().take(100)
        if (id == null) {
            txDao.insert(
                TransactionEntity(
                    accountId = accountId, type = type, amount = amount, category = category, note = cleanNote,
                    date = date.toEpochDay(), source = TxSource.MANUAL, createdAt = System.currentTimeMillis()
                )
            )
        } else {
            val old = txDao.get(id) ?: throw UserError("记录不存在")
            if (old.source != TxSource.MANUAL) throw UserError("自动生成的流水不能修改")
            txDao.update(
                old.copy(accountId = accountId, type = type, amount = amount, category = category, note = cleanNote, date = date.toEpochDay())
            )
        }
    }

    suspend fun deleteTransaction(id: Long) = db.withTransaction {
        val old = txDao.get(id) ?: return@withTransaction
        if (old.source != TxSource.MANUAL) throw UserError("自动生成的流水不能删除")
        txDao.delete(id)
    }
}
