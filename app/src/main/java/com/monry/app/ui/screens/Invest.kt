package com.monry.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.monry.app.AppContainer
import com.monry.app.data.InvestmentSnapshot
import com.monry.app.data.snapshot
import com.monry.app.domain.Money
import com.monry.app.ui.Fmt
import com.monry.app.ui.appViewModel
import com.monry.app.ui.clock
import com.monry.app.ui.components.EmptyHint
import com.monry.app.ui.components.Hairline
import com.monry.app.ui.components.MText
import com.monry.app.ui.components.PageHeader
import com.monry.app.ui.components.RollingMoney
import com.monry.app.ui.components.SectionLabel
import com.monry.app.ui.components.SurfaceCard
import com.monry.app.ui.components.TextAction
import com.monry.app.ui.components.TiltState
import com.monry.app.ui.components.enterAnim
import com.monry.app.ui.components.itemMotion
import com.monry.app.ui.components.enterFlip
import com.monry.app.ui.components.pressable
import com.monry.app.ui.components.rememberGrow
import com.monry.app.ui.components.tilt3d
import com.monry.app.ui.theme.M
import com.monry.app.ui.theme.T
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class InvestListState(val items: List<InvestmentSnapshot>) {
    val active get() = items.filter { it.isActive }
    val ended get() = items.filter { !it.isActive }
    val value get() = active.sumOf { it.value }
    val principal get() = active.sumOf { it.principal }
    val profit get() = active.sumOf { it.profit }
}

class InvestListViewModel(c: AppContainer) : ViewModel() {
    val state: StateFlow<InvestListState?> = combine(c.investments.all(), clock(10_000)) { l, now ->
        InvestListState(l.map { it.snapshot(now) })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun InvestScreen(onNew: () -> Unit, onOpen: (Long) -> Unit) {
    val vm = appViewModel { InvestListViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        PageHeader("投资") { TextAction("＋ 新增", onNew, color = M.c.ink) }
        val s = state ?: return@Column
        if (s.items.isEmpty()) {
            EmptyHint("还没有投资项目\n记录本金、年利率和起始日期，自动计算收益")
            return@Column
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (s.active.isNotEmpty()) item { InvestSummary(s) }
            itemsIndexed(s.active, key = { _, it -> it.id }) { i, snap -> InvestCard(snap, i + 1) { onOpen(snap.id) } }
            if (s.ended.isNotEmpty()) {
                item { SectionLabel("已结束", Modifier.padding(top = 12.dp)) }
                itemsIndexed(s.ended, key = { _, it -> it.id }) { i, snap ->
                    Column(itemMotion().fillMaxWidth().enterAnim(i).pressable { onOpen(snap.id) }) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                MText(snap.name, style = T.body)
                                MText("收益 " + Money.format(snap.profit, signed = true), style = T.caption, color = M.c.inkSoft)
                            }
                            MText(Money.format(snap.value), style = T.amount, color = M.c.inkSoft)
                        }
                        Hairline()
                    }
                }
            }
        }
    }
}

@Composable
private fun InvestSummary(s: InvestListState) {
    val tilt = remember { TiltState() }
    SurfaceCard(Modifier.fillMaxWidth().enterFlip(0).tilt3d(tilt, 5f)) {
        Column(Modifier.padding(24.dp)) {
            MText("投资总市值", style = T.label, color = M.c.inkSoft)
            Spacer(Modifier.height(6.dp))
            RollingMoney(s.value, T.display, prefix = "¥ ")
            Spacer(Modifier.height(18.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    MText("本金", style = T.caption, color = M.c.inkSoft)
                    RollingMoney(s.principal, T.amount)
                }
                Column(Modifier.weight(1f)) {
                    MText("累计收益", style = T.caption, color = M.c.inkSoft)
                    RollingMoney(s.profit, T.amount, color = M.c.accent, signed = true)
                }
            }
        }
    }
}

@Composable
private fun InvestCard(snap: InvestmentSnapshot, index: Int, onClick: () -> Unit) {
    val tilt = remember { TiltState() }
    SurfaceCard(Modifier.fillMaxWidth().enterFlip(index).tilt3d(tilt, 6f).pressable(onClick = onClick)) {
        Column(Modifier.padding(22.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MText(snap.name, Modifier.weight(1f), T.headline, maxLines = 1)
                MText("年化 " + com.monry.app.domain.Percent.format(snap.rateBp) + " · " + snap.mode.label, style = T.caption, color = M.c.inkSoft)
            }
            Spacer(Modifier.height(14.dp))
            RollingMoney(snap.value, T.display.copy(fontSize = 28.sp), prefix = "¥ ")
            Spacer(Modifier.height(6.dp))
            Row {
                MText("累计收益 " + Money.format(snap.profit, signed = true), Modifier.weight(1f), T.caption, M.c.accent)
                MText(
                    (if (snap.projectionIsMaturity) "预计到期收益 " else "预计一年收益 ") + Money.format(snap.projectedProfit, signed = true),
                    style = T.caption, color = M.c.inkSoft
                )
            }
            snap.progress?.let { p ->
                Spacer(Modifier.height(16.dp))
                val grow = rememberGrow(p, 150)
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(M.c.surfaceAlt)) {
                    Box(Modifier.fillMaxWidth(grow).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(M.c.ink))
                }
                Spacer(Modifier.height(6.dp))
                MText("期限 " + Fmt.termText(snap.termMonths) + " · 到期 " + snap.maturity, style = T.caption, color = M.c.inkFaint)
            }
        }
    }
}
