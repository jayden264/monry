package com.monry.app.data

import com.monry.app.domain.AccountType
import com.monry.app.domain.InterestMode
import com.monry.app.domain.InvestStatus
import com.monry.app.domain.Recurrence
import com.monry.app.domain.RuleStatus
import com.monry.app.domain.TxSource
import com.monry.app.domain.TxType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCodecTest {
    private val data = BackupData(
        accounts = listOf(AccountEntity(1, "现金", AccountType.CASH, 12_345, 0, 111L), AccountEntity(7, "银行卡", AccountType.BANK, -50, 1, 222L)),
        transactions = listOf(
            TransactionEntity(3, 1, TxType.EXPENSE, 999_999_999_999L, "餐饮", "含\"引号\"和换行\n与中文", 20_000, TxSource.MANUAL, null, 5L),
            TransactionEntity(4, 7, TxType.INCOME, 100, "工资", "", 20_001, TxSource.RECURRING, 9L, 6L)
        ),
        rules = listOf(RecurringRuleEntity(9, "房租", 300_000, TxType.EXPENSE, 7, "居家", 20_000, 540, Recurrence.MONTHLY, RuleStatus.PAUSED, 4, 2, 8L)),
        executions = listOf(RuleExecutionEntity(2, 9, 2, 3, 20_090, 540, 77L, 300_000, TxType.EXPENSE, 4L), RuleExecutionEntity(3, 9, 2, 4, 20_120, 540, 78L, 300_000, TxType.EXPENSE, null)),
        investments = listOf(
            InvestmentEntity(5, "某公司", 325, InterestMode.COMPOUND, 36, InvestStatus.ENDED, 19_000, 1_700_000_000L, 1_234_567L, 1L, 10L),
            InvestmentEntity(6, "国债", 0, InterestMode.SIMPLE, null, InvestStatus.ACTIVE, 19_500, null, null, null, 11L)
        ),
        tranches = listOf(TrancheEntity(1, 5, 1_000_000, 19_000), TrancheEntity(2, 6, 1, 19_500))
    )

    @Test fun roundTripIsExactIncludingNullsAndSpecialCharacters() {
        assertEquals(data, BackupCodec.decode(BackupCodec.encode(data, 123L)))
    }

    @Test fun emptyDatabaseRoundTrips() {
        val empty = BackupData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty, 1L)))
    }

    @Test fun rejectsForeignOrCorruptFiles() {
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("not json") }
        assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode("{\"app\":\"other\"}") }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(BackupCodec.encode(data, 1L).replace("\"formatVersion\":1", "\"formatVersion\":99"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.decode(BackupCodec.encode(data, 1L).replace("CASH", "GOLD"))
        }
    }
}
