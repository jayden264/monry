package com.monry.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.withTransaction
import com.monry.app.domain.AccountType
import com.monry.app.domain.InterestMode
import com.monry.app.domain.InvestStatus
import com.monry.app.domain.Recurrence
import com.monry.app.domain.RuleStatus
import com.monry.app.domain.TxSource
import com.monry.app.domain.TxType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream

/** Raw table access used only for backup/restore. Not part of the schema. */
@Dao
interface BackupDao {
    @Query("SELECT * FROM accounts") suspend fun accounts(): List<AccountEntity>
    @Query("SELECT * FROM transactions") suspend fun transactions(): List<TransactionEntity>
    @Query("SELECT * FROM recurring_rules") suspend fun rules(): List<RecurringRuleEntity>
    @Query("SELECT * FROM rule_executions") suspend fun executions(): List<RuleExecutionEntity>
    @Query("SELECT * FROM investments") suspend fun investments(): List<InvestmentEntity>
    @Query("SELECT * FROM investment_tranches") suspend fun tranches(): List<TrancheEntity>

    @Insert suspend fun insertAccounts(l: List<AccountEntity>)
    @Insert suspend fun insertTransactions(l: List<TransactionEntity>)
    @Insert suspend fun insertRules(l: List<RecurringRuleEntity>)
    @Insert suspend fun insertExecutions(l: List<RuleExecutionEntity>)
    @Insert suspend fun insertInvestments(l: List<InvestmentEntity>)
    @Insert suspend fun insertTranches(l: List<TrancheEntity>)

    @Query("DELETE FROM rule_executions") suspend fun clearExecutions()
    @Query("DELETE FROM investment_tranches") suspend fun clearTranches()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM recurring_rules") suspend fun clearRules()
    @Query("DELETE FROM investments") suspend fun clearInvestments()
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
}

data class BackupData(
    val accounts: List<AccountEntity>,
    val transactions: List<TransactionEntity>,
    val rules: List<RecurringRuleEntity>,
    val executions: List<RuleExecutionEntity>,
    val investments: List<InvestmentEntity>,
    val tranches: List<TrancheEntity>
)

/** Pure JSON <-> entity mapping. Money stays integer minor units, so a round trip is exact. */
object BackupCodec {
    const val FORMAT_VERSION = 1

    private fun JSONObject.long(k: String) = getLong(k)
    private fun JSONObject.int(k: String) = getInt(k)
    private fun JSONObject.longOrNull(k: String) = if (isNull(k)) null else getLong(k)
    private fun JSONObject.intOrNull(k: String) = if (isNull(k)) null else getInt(k)

    private fun <T> JSONArray.mapObjects(f: (JSONObject) -> T): List<T> = (0 until length()).map { f(getJSONObject(it)) }
    private fun <T> List<T>.toArray(f: (T) -> JSONObject) = JSONArray().also { a -> forEach { a.put(f(it)) } }

    fun encode(d: BackupData, exportedAt: Long): String = JSONObject().apply {
        put("app", "monry")
        put("formatVersion", FORMAT_VERSION)
        put("exportedAt", exportedAt)
        put("accounts", d.accounts.toArray {
            JSONObject().put("id", it.id).put("name", it.name).put("type", it.type.name)
                .put("openingBalance", it.openingBalance).put("sortOrder", it.sortOrder).put("createdAt", it.createdAt)
        })
        put("transactions", d.transactions.toArray {
            JSONObject().put("id", it.id).put("accountId", it.accountId).put("type", it.type.name).put("amount", it.amount)
                .put("category", it.category).put("note", it.note).put("date", it.date).put("source", it.source.name)
                .put("refId", it.refId ?: JSONObject.NULL).put("createdAt", it.createdAt)
        })
        put("rules", d.rules.toArray {
            JSONObject().put("id", it.id).put("name", it.name).put("amount", it.amount).put("type", it.type.name)
                .put("accountId", it.accountId).put("category", it.category).put("startEpochDay", it.startEpochDay)
                .put("startMinuteOfDay", it.startMinuteOfDay).put("recurrence", it.recurrence.name)
                .put("status", it.status.name).put("nextIndex", it.nextIndex).put("generation", it.generation)
                .put("createdAt", it.createdAt)
        })
        put("executions", d.executions.toArray {
            JSONObject().put("id", it.id).put("ruleId", it.ruleId).put("generation", it.generation)
                .put("occurrenceIndex", it.occurrenceIndex).put("scheduledEpochDay", it.scheduledEpochDay)
                .put("scheduledMinuteOfDay", it.scheduledMinuteOfDay).put("executedAt", it.executedAt)
                .put("amount", it.amount).put("type", it.type.name).put("txId", it.txId ?: JSONObject.NULL)
        })
        put("investments", d.investments.toArray {
            JSONObject().put("id", it.id).put("name", it.name).put("rateBp", it.rateBp).put("mode", it.mode.name)
                .put("termMonths", it.termMonths ?: JSONObject.NULL).put("status", it.status.name)
                .put("startEpochDay", it.startEpochDay).put("endEpochSecond", it.endEpochSecond ?: JSONObject.NULL)
                .put("redeemedAmount", it.redeemedAmount ?: JSONObject.NULL)
                .put("redeemAccountId", it.redeemAccountId ?: JSONObject.NULL).put("createdAt", it.createdAt)
        })
        put("tranches", d.tranches.toArray {
            JSONObject().put("id", it.id).put("investmentId", it.investmentId).put("amount", it.amount).put("epochDay", it.epochDay)
        })
    }.toString()

    /** Throws [IllegalArgumentException] for anything that is not a Monry backup this version understands. */
    fun decode(text: String): BackupData {
        try {
            val root = JSONObject(text)
            require(root.optString("app") == "monry") { "not a Monry backup" }
            require(root.getInt("formatVersion") <= FORMAT_VERSION) { "backup is from a newer version" }
            return BackupData(
                accounts = root.getJSONArray("accounts").mapObjects {
                    AccountEntity(it.long("id"), it.getString("name"), AccountType.valueOf(it.getString("type")),
                        it.long("openingBalance"), it.int("sortOrder"), it.long("createdAt"))
                },
                transactions = root.getJSONArray("transactions").mapObjects {
                    TransactionEntity(it.long("id"), it.long("accountId"), TxType.valueOf(it.getString("type")), it.long("amount"),
                        it.getString("category"), it.getString("note"), it.long("date"),
                        TxSource.valueOf(it.getString("source")), it.longOrNull("refId"), it.long("createdAt"))
                },
                rules = root.getJSONArray("rules").mapObjects {
                    RecurringRuleEntity(it.long("id"), it.getString("name"), it.long("amount"), TxType.valueOf(it.getString("type")),
                        it.long("accountId"), it.getString("category"), it.long("startEpochDay"), it.int("startMinuteOfDay"),
                        Recurrence.valueOf(it.getString("recurrence")), RuleStatus.valueOf(it.getString("status")),
                        it.int("nextIndex"), it.int("generation"), it.long("createdAt"))
                },
                executions = root.getJSONArray("executions").mapObjects {
                    RuleExecutionEntity(it.long("id"), it.long("ruleId"), it.int("generation"), it.int("occurrenceIndex"),
                        it.long("scheduledEpochDay"), it.int("scheduledMinuteOfDay"), it.long("executedAt"), it.long("amount"),
                        TxType.valueOf(it.getString("type")), it.longOrNull("txId"))
                },
                investments = root.getJSONArray("investments").mapObjects {
                    InvestmentEntity(it.long("id"), it.getString("name"), it.int("rateBp"), InterestMode.valueOf(it.getString("mode")),
                        it.intOrNull("termMonths"), InvestStatus.valueOf(it.getString("status")), it.long("startEpochDay"),
                        it.longOrNull("endEpochSecond"), it.longOrNull("redeemedAmount"), it.longOrNull("redeemAccountId"),
                        it.long("createdAt"))
                },
                tranches = root.getJSONArray("tranches").mapObjects {
                    TrancheEntity(it.long("id"), it.long("investmentId"), it.long("amount"), it.long("epochDay"))
                }
            )
        } catch (e: JSONException) {
            throw IllegalArgumentException("malformed backup", e)
        }
    }
}

class BackupRepository(private val db: MonryDatabase) {
    private val dao = db.backup()

    /** Writes a consistent snapshot of every table to [out]. */
    suspend fun export(out: OutputStream, nowMillis: Long) {
        val data = db.withTransaction {
            BackupData(dao.accounts(), dao.transactions(), dao.rules(), dao.executions(), dao.investments(), dao.tranches())
        }
        val text = BackupCodec.encode(data, nowMillis)
        withContext(Dispatchers.IO) { out.bufferedWriter(Charsets.UTF_8).use { it.write(text) } }
    }

    /**
     * Replaces ALL current data with the backup. The file is fully parsed before anything is deleted,
     * and the replacement is one transaction, so a bad file leaves the existing data untouched.
     */
    suspend fun import(input: InputStream) {
        val text = withContext(Dispatchers.IO) { input.bufferedReader(Charsets.UTF_8).use { it.readText() } }
        val data = try {
            BackupCodec.decode(text)
        } catch (e: IllegalArgumentException) {
            throw UserError("这不是有效的 Monry 备份文件，或版本过新")
        }
        try {
            db.withTransaction {
                dao.clearExecutions(); dao.clearTranches(); dao.clearTransactions()
                dao.clearRules(); dao.clearInvestments(); dao.clearAccounts()
                dao.insertAccounts(data.accounts); dao.insertRules(data.rules); dao.insertInvestments(data.investments)
                dao.insertTransactions(data.transactions); dao.insertExecutions(data.executions); dao.insertTranches(data.tranches)
            }
        } catch (e: android.database.SQLException) {
            throw UserError("备份文件内容不一致，未做任何改动")
        }
    }
}
