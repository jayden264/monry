package com.monry.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.monry.app.AppContainer
import com.monry.app.data.TransactionEntity
import com.monry.app.domain.Money
import com.monry.app.domain.TxSource
import com.monry.app.domain.TxType
import com.monry.app.ui.Fmt
import com.monry.app.ui.appViewModel
import com.monry.app.ui.components.EmptyHint
import com.monry.app.ui.components.LocalToast
import com.monry.app.ui.components.MText
import com.monry.app.ui.components.PageHeader
import com.monry.app.ui.components.Segmented
import com.monry.app.ui.components.RollingMoney
import com.monry.app.ui.components.enterAnim
import com.monry.app.ui.components.pressable
import com.monry.app.ui.theme.M
import com.monry.app.ui.theme.T
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class LedgerState(val month: YearMonth, val txs: List<TransactionEntity>, val accountNames: Map<Long, String>)

@OptIn(ExperimentalCoroutinesApi::class)
class LedgerViewModel(c: AppContainer) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())

    val state: StateFlow<LedgerState?> = combine(
        month.flatMapLatest { c.ledger.range(it.atDay(1), it.atEndOfMonth()) }, c.ledger.accounts, month
    ) { txs, accounts, m -> LedgerState(m, txs, accounts.associate { it.account.id to it.account.name }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun shift(delta: Int) {
        val next = month.value.plusMonths(delta.toLong())
        if (!next.isAfter(YearMonth.now())) month.value = next
    }
}

@Composable
fun LedgerScreen(onBack: () -> Unit, onEditTx: (Long) -> Unit) {
    val vm = appViewModel { LedgerViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    var filter by remember { mutableIntStateOf(0) }
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize()) {
        PageHeader("流水", onBack = onBack)
        val s = state ?: return@Column
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            MText("‹", Modifier.pressable { vm.shift(-1) }.padding(end = 20.dp, top = 4.dp, bottom = 4.dp), T.display, M.c.inkSoft)
            MText("${s.month.year} 年 ${s.month.monthValue} 月", Modifier.weight(1f), T.headline)
            MText("›", Modifier.pressable { vm.shift(1) }.padding(start = 20.dp, top = 4.dp, bottom = 4.dp), T.display, M.c.inkSoft)
        }
        val counted = s.txs.filter { it.source != TxSource.INVESTMENT }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Column(Modifier.weight(1f)) {
                MText("收入", style = T.caption, color = M.c.inkSoft)
                RollingMoney(counted.filter { it.type == TxType.INCOME }.sumOf { it.amount }, T.amount, color = M.c.accent)
            }
            Column(Modifier.weight(1f)) {
                MText("支出", style = T.caption, color = M.c.inkSoft)
                RollingMoney(counted.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }, T.amount)
            }
        }
        Segmented(listOf("全部", "支出", "收入"), filter, { filter = it }, Modifier.padding(horizontal = 24.dp))
        val shown = s.txs.filter { filter == 0 || (filter == 1 && it.type == TxType.EXPENSE) || (filter == 2 && it.type == TxType.INCOME) }
        if (shown.isEmpty()) {
            EmptyHint("这个月没有流水")
        } else {
            val groups = shown.groupBy { it.date }.toList()
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 24.dp)) {
                groups.forEach { (day, list) ->
                    item(key = "d$day") {
                        val date = LocalDate.ofEpochDay(day)
                        val net = list.filter { it.source != TxSource.INVESTMENT }.sumOf { it.type.sign * it.amount }
                        Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 2.dp)) {
                            MText(Fmt.dayLabel(date, today), Modifier.weight(1f), T.label, M.c.inkSoft)
                            MText(Money.format(net, signed = true), style = T.caption, color = M.c.inkSoft)
                        }
                    }
                    items(list, key = { it.id }) { tx ->
                        TxRow(
                            tx, s.accountNames[tx.accountId],
                            onClick = {
                                if (tx.source == TxSource.MANUAL) onEditTx(tx.id) else toast("自动生成的流水不能编辑")
                            }
                        )
                    }
                }
            }
        }
    }
}
