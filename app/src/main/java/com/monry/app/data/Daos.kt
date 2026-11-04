package com.monry.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query(
        "SELECT a.*, a.openingBalance + COALESCE((SELECT SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE -t.amount END) " +
            "FROM transactions t WHERE t.accountId = a.id), 0) AS balance FROM accounts a ORDER BY a.sortOrder, a.id"
    )
    fun observeWithBalance(): Flow<List<AccountWithBalance>>

    @Query(
        "SELECT a.openingBalance + COALESCE((SELECT SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE -t.amount END) " +
            "FROM transactions t WHERE t.accountId = a.id), 0) FROM accounts a WHERE a.id = :id"
    )
    suspend fun balanceOf(id: Long): Long?

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    suspend fun count(): Int

    @Insert
    suspend fun insert(a: AccountEntity): Long

    @Update
    suspend fun update(a: AccountEntity)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(t: TransactionEntity): Long

    @Update
    suspend fun update(t: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY date DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :from AND :to ORDER BY date DESC, id DESC")
    fun observeRange(from: Long, to: Long): Flow<List<TransactionEntity>>
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM recurring_rules ORDER BY CASE status WHEN 'ACTIVE' THEN 0 WHEN 'PAUSED' THEN 1 ELSE 2 END, id DESC")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun get(id: Long): RecurringRuleEntity?

    @Query("SELECT * FROM recurring_rules WHERE status = 'ACTIVE'")
    suspend fun active(): List<RecurringRuleEntity>

    @Insert
    suspend fun insert(r: RecurringRuleEntity): Long

    @Update
    suspend fun update(r: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE recurring_rules SET nextIndex = MAX(nextIndex, :next) WHERE id = :id AND generation = :generation")
    suspend fun advance(id: Long, generation: Int, next: Int)

    @Query("UPDATE recurring_rules SET status = 'COMPLETED' WHERE id = :id AND generation = :generation")
    suspend fun complete(id: Long, generation: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExecution(e: RuleExecutionEntity): Long

    @Query("UPDATE rule_executions SET txId = :txId WHERE id = :id")
    suspend fun setExecutionTx(id: Long, txId: Long)

    @Query("SELECT * FROM rule_executions WHERE ruleId = :ruleId ORDER BY scheduledEpochDay DESC, scheduledMinuteOfDay DESC, id DESC")
    fun observeExecutions(ruleId: Long): Flow<List<RuleExecutionEntity>>
}

@Dao
interface InvestmentDao {
    @Transaction
    @Query("SELECT * FROM investments ORDER BY status, startEpochDay DESC, id DESC")
    fun observeAll(): Flow<List<InvestmentWithTranches>>

    @Transaction
    @Query("SELECT * FROM investments WHERE id = :id")
    fun observe(id: Long): Flow<InvestmentWithTranches?>

    @Transaction
    @Query("SELECT * FROM investments WHERE id = :id")
    suspend fun get(id: Long): InvestmentWithTranches?

    @Insert
    suspend fun insert(i: InvestmentEntity): Long

    @Insert
    suspend fun insertTranche(t: TrancheEntity): Long

    @Update
    suspend fun update(i: InvestmentEntity)

    @Query("DELETE FROM investments WHERE id = :id")
    suspend fun delete(id: Long)
}
