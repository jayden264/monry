package com.monry.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.monry.app.AppContainer
import com.monry.app.data.AccountWithBalance
import com.monry.app.data.TransactionEntity
import com.monry.app.data.UserError
import com.monry.app.domain.Categories
import com.monry.app.domain.Money
import com.monry.app.domain.Parsed
import com.monry.app.domain.TxSource
import com.monry.app.domain.TxType
import com.monry.app.ui.appViewModel
import com.monry.app.ui.components.AccountChips
import com.monry.app.ui.components.AmountField
import com.monry.app.ui.components.ButtonKind
import com.monry.app.ui.components.ChipFlow
import com.monry.app.ui.components.DatePickerRow
import com.monry.app.ui.components.ErrorText
import com.monry.app.ui.components.LineField
import com.monry.app.ui.components.LocalToast
import com.monry.app.ui.components.MButton
import com.monry.app.ui.components.MText
import com.monry.app.ui.components.PageHeader
import com.monry.app.ui.components.SectionLabel
import com.monry.app.ui.components.Segmented
import com.monry.app.ui.components.enterAnim
import com.monry.app.ui.theme.M
import com.monry.app.ui.theme.T
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class AddTxViewModel(private val c: AppContainer) : ViewModel() {
    val accounts: StateFlow<List<AccountWithBalance>> =
        c.ledger.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun load(id: Long): TransactionEntity? = c.ledger.transaction(id)

    fun save(
        id: Long?, accountId: Long?, type: TxType, amount: Long, category: String, note: String, date: LocalDate,
        onDone: () -> Unit, onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                c.ledger.saveTransaction(id, accountId, type, amount, category, note, date, LocalDate.now())
                onDone()
            } catch (e: UserError) {
                onError(e.message ?: "保存失败")
            }
        }
    }

    fun delete(id: Long, onDone: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                c.ledger.deleteTransaction(id)
                onDone()
            } catch (e: UserError) {
                onError(e.message ?: "删除失败")
            }
        }
    }
}

/** [txId] == -1 creates a new entry (the "记账" tab); otherwise edits that entry. */
@Composable
fun AddTxScreen(txId: Long, onBack: () -> Unit, onSaved: () -> Unit) {
    val vm = appViewModel { AddTxViewModel(it) }
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val editing = txId != -1L

    var typeIdx by rememberSaveable(txId) { mutableIntStateOf(0) }
    var amountText by rememberSaveable(txId) { mutableStateOf("") }
    var category by rememberSaveable(txId) { mutableStateOf("餐饮") }
    var accountId by rememberSaveable(txId) { mutableLongStateOf(-1L) }
    var note by rememberSaveable(txId) { mutableStateOf("") }
    var dayEpoch by rememberSaveable(txId) { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var error by rememberSaveable(txId) { mutableStateOf<String?>(null) }
    var readOnly by rememberSaveable(txId) { mutableStateOf(false) }
    val type = if (typeIdx == 0) TxType.EXPENSE else TxType.INCOME

    LaunchedEffect(txId) {
        if (editing) {
            vm.load(txId)?.let {
                typeIdx = if (it.type == TxType.EXPENSE) 0 else 1
                amountText = Money.toInputText(it.amount)
                category = it.category
                accountId = it.accountId
                note = it.note
                dayEpoch = it.date
                readOnly = it.source != TxSource.MANUAL
            }
        }
    }
    LaunchedEffect(accounts) {
        if (accountId == -1L || accounts.none { it.account.id == accountId }) {
            accounts.firstOrNull()?.let { accountId = it.account.id }
        }
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        PageHeader(if (editing) "编辑流水" else "记一笔", onBack = if (editing) onBack else null)
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Segmented(listOf("支出", "收入"), typeIdx, {
                typeIdx = it
                val list = Categories.of(if (it == 0) TxType.EXPENSE else TxType.INCOME)
                if (category !in list) category = list.first()
            })
            Spacer(Modifier.height(28.dp))
            AmountField(amountText, { amountText = it; error = null }, Modifier.enterAnim(0), label = "金额")
            Spacer(Modifier.height(28.dp))
            SectionLabel("分类")
            ChipFlow(Categories.of(type), category, { category = it })
            Spacer(Modifier.height(28.dp))
            SectionLabel("账户")
            AccountChips(accounts, accountId, { accountId = it })
            Spacer(Modifier.height(12.dp))
            DatePickerRow("日期", LocalDate.ofEpochDay(dayEpoch), { dayEpoch = it.toEpochDay() }, max = LocalDate.now())
            Spacer(Modifier.height(12.dp))
            LineField(note, { note = it.take(100) }, "备注", placeholder = "可选")
            ErrorText(error)
            Spacer(Modifier.height(32.dp))
            if (readOnly) {
                MText("这条流水由定时规则或投资自动生成，不能修改。", style = T.caption, color = M.c.inkSoft)
            } else {
                MButton("保存", {
                    when (val a = Money.parsePositive(amountText)) {
                        is Parsed.Err -> error = a.message
                        is Parsed.Ok -> vm.save(
                            if (editing) txId else null, accountId.takeIf { it != -1L }, type, a.value, category, note,
                            LocalDate.ofEpochDay(dayEpoch),
                            onDone = {
                                toast(if (editing) "已保存" else "已记一笔")
                                amountText = ""
                                note = ""
                                error = null
                                onSaved()
                            },
                            onError = { error = it }
                        )
                    }
                }, Modifier.fillMaxWidth())
                if (editing) {
                    Spacer(Modifier.height(12.dp))
                    MButton("删除这笔流水", {
                        vm.delete(txId, onDone = { toast("已删除"); onBack() }, onError = { error = it })
                    }, Modifier.fillMaxWidth(), ButtonKind.Danger)
                }
            }
        }
    }
}
