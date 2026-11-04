package com.monry.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.monry.app.AppContainer
import com.monry.app.data.AccountWithBalance
import com.monry.app.data.TransactionEntity
import com.monry.app.data.snapshot
import com.monry.app.domain.Money
import com.monry.app.domain.TxSource
import com.monry.app.domain.TxType
import com.monry.app.ui.Fmt
import com.monry.app.ui.appViewModel
import com.monry.app.ui.clock
import com.monry.app.ui.components.EmptyHint
import com.monry.app.ui.components.Hairline
import com.monry.app.ui.components.MText
import com.monry.app.ui.components.RollingMoney
import com.monry.app.ui.components.SectionLabel
import com.monry.app.ui.components.TextAction
import com.monry.app.ui.components.TiltState
import com.monry.app.ui.components.enterAnim
import com.monry.app.ui.components.pressable
import com.monry.app.ui.components.tilt3d
import com.monry.app.ui.theme.LocalReduceMotion
import com.monry.app.ui.theme.M
import com.monry.app.ui.theme.T
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class HomeState(
    val accounts: List<AccountWithBalance>,
    val recent: List<TransactionEntity>,
    val accountsTotal: Long,
    val investValue: Long,
    val investProfit: Long,
    val monthIncome: Long,
    val monthExpense: Long,
    val today: LocalDate
) {
    /** Cash-like balances plus the live value of every running investment. */
    val total: Long get() = accountsTotal + investValue
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(c: AppContainer) : ViewModel() {
    private val clock = clock(30_000)
    private val monthTx = clock.map { YearMonth.from(it) }.distinctUntilChanged()
        .flatMapLatest { c.ledger.range(it.atDay(1), it.atEndOfMonth()) }

    val state: StateFlow<HomeState?> = combine(
        c.ledger.accounts, c.ledger.recent(6), c.investments.all(), monthTx, clock
    ) { accounts, recent, invs, month, now ->
        val live = invs.filter { it.investment.status == com.monry.app.domain.InvestStatus.ACTIVE }.map { it.snapshot(now) }
        val counted = month.filter { it.source != TxSource.INVESTMENT }
        HomeState(
            accounts = accounts,
            recent = recent,
            accountsTotal = accounts.sumOf { it.balance },
            investValue = live.sumOf { it.value },
            investProfit = live.sumOf { it.profit },
            monthIncome = counted.filter { it.type == TxType.INCOME }.sumOf { it.amount },
            monthExpense = counted.filter { it.type == TxType.EXPENSE }.sumOf { it.amount },
            today = now.toLocalDate()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun HomeScreen(onSettings: () -> Unit, onLedger: () -> Unit, onEditTx: (Long) -> Unit, onInvest: () -> Unit) {
    val vm = appViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val s = state
    if (s == null) {
        Box(Modifier.fillMaxSize())
        return
    }
    val listState = rememberLazyListState()
    val scroll: () -> Float = {
        if (listState.firstVisibleItemIndex == 0) listState.firstVisibleItemScrollOffset.toFloat() else 1200f
    }
    val names = s.accounts.associate { it.account.id to it.account.name }

    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    MText(Fmt.monthDay(s.today) + " " + Fmt.weekday(s.today), style = T.caption, color = M.c.inkSoft)
                    MText("总览", style = T.title)
                }
                TextAction("设置", onSettings)
            }
        }
        item { AssetHero(s.total, s.accountsTotal, s.investValue, scroll, Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) }
        item {
            Row(Modifier.padding(horizontal = 24.dp, vertical = 20.dp).enterAnim(1)) {
                MonthStat("本月收入", s.monthIncome, M.c.accent, Modifier.weight(1f))
                MonthStat("本月支出", s.monthExpense, M.c.ink, Modifier.weight(1f))
            }
        }
        item {
            Column(Modifier.padding(horizontal = 24.dp).padding(top = 8.dp).enterAnim(2)) {
                SectionLabel("账户")
                s.accounts.forEach { a ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            MText(a.account.name, style = T.body)
                            MText(a.account.type.label, style = T.caption, color = M.c.inkSoft)
                        }
                        MText(Money.format(a.balance), style = T.amount, color = if (a.balance < 0) M.c.danger else M.c.ink)
                    }
                    Hairline()
                }
                if (s.investValue > 0) {
                    Row(
                        Modifier.fillMaxWidth().pressable(onClick = onInvest).padding(vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            MText("投资", style = T.body)
                            MText("累计收益 " + Money.format(s.investProfit, signed = true), style = T.caption, color = M.c.accent)
                        }
                        MText(Money.format(s.investValue), style = T.amount)
                    }
                    Hairline()
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 28.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MText("近期流水", Modifier.weight(1f), T.headline)
                TextAction("全部", onLedger)
            }
        }
        if (s.recent.isEmpty()) {
            item { EmptyHint("还没有流水\n点击下方「记账」开始第一笔") }
        } else {
            items(s.recent, key = { it.id }) { tx ->
                TxRow(
                    tx, names[tx.accountId], onClick = { if (tx.source == TxSource.MANUAL) onEditTx(tx.id) },
                    modifier = Modifier.padding(horizontal = 24.dp).enterAnim(s.recent.indexOf(tx) + 2),
                    subtitle = Fmt.dayLabel(LocalDate.ofEpochDay(tx.date), s.today)
                )
            }
        }
    }
}

@Composable
private fun MonthStat(label: String, minor: Long, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        MText(label, style = T.caption, color = M.c.inkSoft)
        Spacer(Modifier.height(4.dp))
        RollingMoney(minor, T.display.copy(fontSize = 24.sp), color = color)
    }
}

/** Three stacked cards with different parallax speeds; the front one tilts toward the finger. */
@Composable
private fun AssetHero(total: Long, accounts: Long, invest: Long, scroll: () -> Float, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val tilt = remember { TiltState() }
    val enter = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) enter.animateTo(1f, tween(1000, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
    }
    val sheen by animateFloatAsState(tilt.ry / 7f, label = "sheen")
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(28.dp)
    val c = M.c

    Box(modifier.fillMaxWidth().height(232.dp)) {
        listOf(2, 1).forEach { layer ->
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(184.dp)
                    .graphicsLayer {
                        val e = enter.value
                        translationY = scroll() * (0.28f - layer * 0.10f) + layer * 22.dp.toPx() * e
                        val sc = 1f - layer * 0.055f
                        scaleX = sc
                        scaleY = sc
                        alpha = (1f - layer * 0.38f) * e
                    }
                    .clip(shape).background(c.heroBg)
            )
        }
        Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().height(184.dp)
                .graphicsLayer { translationY = scroll() * 0.28f }
                .tilt3d(tilt, 7f)
                .graphicsLayer {
                    val e = enter.value
                    alpha = e
                    rotationX = (1f - e) * -26f
                    translationY = (1f - e) * 36.dp.toPx()
                    cameraDistance = 14f * density
                }
                .clip(shape).background(c.heroBg)
        ) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.linearGradient(
                        listOf(Color.Transparent, c.heroInk.copy(alpha = 0.10f), Color.Transparent),
                        start = Offset(300f * sheen, 0f), end = Offset(300f * sheen + 520f, 360f)
                    )
                )
            )
            Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Column {
                    MText("总资产", style = T.label, color = c.heroInkSoft)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        MText("¥", Modifier.padding(end = 4.dp, bottom = 4.dp), T.display.copy(fontSize = 22.sp), c.heroInkSoft)
                        val big = Money.format(total).length > 12
                        RollingMoney(total, if (big) T.hero.copy(fontSize = 32.sp) else T.hero, color = c.heroInk)
                    }
                }
                Row {
                    HeroFigure("账户", accounts, Modifier.weight(1f))
                    HeroFigure("投资", invest, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeroFigure(label: String, minor: Long, modifier: Modifier = Modifier) {
    Column(modifier) {
        MText(label, style = T.caption, color = M.c.heroInkSoft)
        Spacer(Modifier.width(2.dp))
        RollingMoney(minor, T.amount, color = M.c.heroInk, prefix = "¥ ")
    }
}
