package com.monry.app.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.monry.app.domain.AccountType
import com.monry.app.domain.InterestMode
import com.monry.app.domain.InvestStatus
import com.monry.app.domain.Recurrence
import com.monry.app.domain.RuleStatus
import com.monry.app.domain.TxSource
import com.monry.app.domain.TxType

// All money columns are Long minor units (fen). All dates are epoch days (wall-clock local dates).

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    /** Balance before any ledger entry; current balance = opening + sum(ledger). */
    val openingBalance: Long,
    val sortOrder: Int = 0,
    val createdAt: Long
)

data class AccountWithBalance(
    @Embedded val account: AccountEntity,
    val balance: Long
)

@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("accountId"), Index("date")]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: Long,
    val type: TxType,
    val amount: Long,
    val category: String,
    val note: String,
    val date: Long,
    val source: TxSource,
    /** Rule id or investment id, depending on [source]. */
    val refId: Long? = null,
    val createdAt: Long
)

@Entity(
    tableName = "recurring_rules",
    foreignKeys = [ForeignKey(entity = AccountEntity::class, parentColumns = ["id"], childColumns = ["accountId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("accountId")]
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    val type: TxType,
    val accountId: Long,
    val category: String,
    val startEpochDay: Long,
    val startMinuteOfDay: Int,
    val recurrence: Recurrence,
    val status: RuleStatus,
    val nextIndex: Int,
    val generation: Int = 0,
    val createdAt: Long
)

@Entity(
    tableName = "rule_executions",
    foreignKeys = [ForeignKey(entity = RecurringRuleEntity::class, parentColumns = ["id"], childColumns = ["ruleId"], onDelete = ForeignKey.CASCADE)],
    // The unique key is what makes catch-up idempotent: one row per occurrence, ever.
    indices = [Index(value = ["ruleId", "generation", "occurrenceIndex"], unique = true)]
)
data class RuleExecutionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ruleId: Long,
    val generation: Int,
    val occurrenceIndex: Int,
    val scheduledEpochDay: Long,
    val scheduledMinuteOfDay: Int,
    val executedAt: Long,
    val amount: Long,
    val type: TxType,
    val txId: Long? = null
)

@Entity(tableName = "investments")
data class InvestmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Annual rate in basis points of a percent: 3.25% == 325. */
    val rateBp: Int,
    val mode: InterestMode,
    val termMonths: Int?,
    val status: InvestStatus,
    val startEpochDay: Long,
    /** Wall-clock moment of redemption, encoded as LocalDateTime.toEpochSecond(UTC). */
    val endEpochSecond: Long? = null,
    val redeemedAmount: Long? = null,
    val redeemAccountId: Long? = null,
    val createdAt: Long
)

@Entity(
    tableName = "investment_tranches",
    foreignKeys = [ForeignKey(entity = InvestmentEntity::class, parentColumns = ["id"], childColumns = ["investmentId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("investmentId")]
)
data class TrancheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val investmentId: Long,
    val amount: Long,
    @ColumnInfo(name = "epochDay") val epochDay: Long
)

data class InvestmentWithTranches(
    @Embedded val investment: InvestmentEntity,
    @Relation(parentColumn = "id", entityColumn = "investmentId")
    val tranches: List<TrancheEntity>
)
