package com.monry.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.monry.app.AppContainer
import com.monry.app.domain.CategoryShare
import com.monry.app.domain.Money
import com.monry.app.domain.StatEntry
import com.monry.app.domain.StatPeriod
import com.monry.app.domain.StatSummary
import com.monry.app.domain.StatsCalculator
import com.monry.app.domain.TxSource
import com.monry.app.ui.appViewModel
import com.monry.app.ui.components.BucketBars
import com.monry.app.ui.components.EmptyHint
import com.monry.app.ui.components.MText
import com.monry.app.ui.components.PageHeader
import com.monry.app.ui.components.RollingMoney
import com.monry.app.ui.components.SectionLabel
import com.monry.app.ui.components.Segmented
import com.monry.app.ui.components.pressable
import com.monry.app.ui.components.rememberGrow
import com.monry.app.ui.theme.M
import com.monry.app.ui.theme.T
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class StatsState(val period: StatPeriod, val anchor: LocalDate, val summary: StatSummary)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(c: AppContainer) : ViewModel() {
    private val period = MutableStateFlow(StatPeriod.MONTH)
    private val anchor = MutableStateFlow(LocalDate.now())

    val state: StateFlow<StatsState?> = combine(period, anchor) { p, a -> p to a }
        .flatMapLatest { (p, a) ->
            val (from, to) = StatsCalculator.range(p, a)
            c.ledger.range(from, to).map { txs ->
                // Moving money into / out of an investment is a transfer, not income or spending.
                val entries = txs.filter { it.source != TxSource.INVESTMENT }
                    .map { StatEntry(it.type, it.amount, it.category, LocalDate.ofEpochDay(it.date)) }
                StatsState(p, a, StatsCalculator.summarize(entries, p, a))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setPeriod(p: StatPeriod) { period.value = p }

    fun shift(delta: Int) {
        val next = StatsCalculator.shift(period.value, anchor.value, delta)
        if (!StatsCalculator.range(period.value, next).first.isAfter(LocalDate.now())) anchor.value = next
    }
}

@Composable
fun StatsScreen() {
    val vm = appViewModel { StatsViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showIncome by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        PageHeader("统计")
        val s = state ?: return@Column
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Segmented(StatPeriod.entries.map { it.label }, s.period.ordinal, { vm.setPeriod(StatPeriod.entries[it]) })
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                MText("‹", Modifier.pressable { vm.shift(-1) }.padding(end = 20.dp), T.display, M.c.inkSoft)
                val label = when (s.period) {
                    StatPeriod.DAY -> s.anchor.toString()
                    StatPeriod.MONTH -> "${s.anchor.year} 年 ${s.anchor.monthValue} 月"
                    StatPeriod.YEAR -> "${s.anchor.year} 年"
                }
                MText(label, Modifier.weight(1f), T.headline)
                MText("›", Modifier.pressable { vm.shift(1) }.padding(start = 20.dp), T.display, M.c.inkSoft)
            }
            Row {
                Column(Modifier.weight(1f)) {
                    MText("收入", style = T.caption, color = M.c.inkSoft)
                    RollingMoney(s.summary.income, T.display.copy(fontSize = 24.sp), color = M.c.accent)
                }
                Column(Modifier.weight(1f)) {
                    MText("支出", style = T.caption, color = M.c.inkSoft)
                    RollingMoney(s.summary.expense, T.display.copy(fontSize = 24.sp))
                }
            }
            Spacer(Modifier.height(10.dp))
            MText("结余 " + Money.format(s.summary.net, signed = true), style = T.caption, color = M.c.inkSoft)
            Spacer(Modifier.height(24.dp))
            Segmented(listOf("支出", "收入"), showIncome, { showIncome = it })
            Spacer(Modifier.height(20.dp))
            val inc = showIncome == 1
            if (s.summary.buckets.isNotEmpty()) {
                BucketBars(s.summary.buckets, inc)
                Spacer(Modifier.height(24.dp))
            }
            val shares = if (inc) s.summary.incomeByCategory else s.summary.expenseByCategory
            SectionLabel("分类占比")
            if (shares.isEmpty()) EmptyHint("这段时间没有记录") else {
                val total = shares.sumOf { it.amount }.coerceAtLeast(1L)
                shares.forEach { ShareRow(it, total) }
            }
        }
    }
}

@Composable
private fun ShareRow(share: CategoryShare, total: Long) {
    val frac = share.amount.toFloat() / total
    val grow = rememberGrow(frac)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row {
            MText(share.category, Modifier.weight(1f), T.body)
            MText(Money.format(share.amount) + "  " + "%.0f%%".format(frac * 100), style = T.caption, color = M.c.inkSoft)
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(M.c.surfaceAlt)) {
            Box(Modifier.fillMaxWidth(grow).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(M.c.ink))
        }
    }
}
