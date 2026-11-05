@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.monry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.monry.app.AppContainer
import com.monry.app.data.AccountWithBalance
import com.monry.app.data.InvestmentSnapshot
import com.monry.app.data.UserError
import com.monry.app.data.snapshot
import com.monry.app.domain.InterestMode
import com.monry.app.domain.InvestmentCalculator
import com.monry.app.domain.Money
import com.monry.app.domain.Parsed
import com.monry.app.domain.Percent
import com.monry.app.domain.SeriesPoint
import java.time.ZoneOffset
import com.monry.app.ui.Fmt
import com.monry.app.ui.appViewModel
import com.monry.app.ui.clock
import com.monry.app.ui.components.AmountField
import com.monry.app.ui.components.ButtonKind
import com.monry.app.ui.components.Chip
import com.monry.app.ui.components.DatePickerRow
import com.monry.app.ui.components.ErrorText
import com.monry.app.ui.components.Hairline
import com.monry.app.ui.components.LineField
import com.monry.app.ui.components.LocalToast
import com.monry.app.ui.components.MButton
import com.monry.app.ui.components.MDialog
import com.monry.app.ui.components.MText
import com.monry.app.ui.components.PageHeader
import com.monry.app.ui.components.ProfitChart
import com.monry.app.ui.components.SectionLabel
import com.monry.app.ui.components.Segmented
import com.monry.app.ui.components.TextAction
import com.monry.app.ui.components.enterAnim
import com.monry.app.ui.components.pressable
import com.monry.app.ui.appContainer
import androidx.compose.runtime.remember
import com.monry.app.ui.theme.M
import com.monry.app.ui.theme.T
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

data class InvestDetailState(
    val snap: InvestmentSnapshot,
    val series: List<SeriesPoint>,
    val now: LocalDateTime,
    val accounts: List<AccountWithBalance>
)

class InvestDetailViewModel(private val c: AppContainer, private val id: Long) : ViewModel() {
    val state: StateFlow<InvestDetailState?> =
        combine(c.investments.observe(id), clock(1_000), c.ledger.accounts) { inv, now, accounts ->
            inv?.let {
                val snap = it.snapshot(now)
                val end = snap.endAt ?: minOf(now, snap.maturity?.atStartOfDay() ?: now)
                val upTo = if (snap.maturity != null) snap.maturity.atStartOfDay() else now.plusYears(1)
                // Series runs to maturity (or one year ahead); the chart draws everything after "now" dashed.
                InvestDetailState(snap, InvestmentCalculator.series(snap.tranches, snap.terms, maxOf(upTo, end)), now, accounts)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun run(onError: (String) -> Unit, onOk: () -> Unit = {}, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                onOk()
            } catch (e: UserError) {
                onError(e.message ?: "操作失败")
            }
        }
    }

    fun topUp(amount: Long, date: LocalDate, source: Long?, onError: (String) -> Unit, onOk: () -> Unit) =
        run(onError, onOk) { c.investments.topUp(id, amount, date, source, LocalDate.now()) }

    fun redeem(account: Long, onError: (String) -> Unit, onOk: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                onOk(c.investments.redeem(id, account, LocalDateTime.now()))
            } catch (e: UserError) {
                onError(e.message ?: "操作失败")
            }
        }
    }

    fun updateTerms(name: String, rateBp: Int, mode: InterestMode, term: Int?, onError: (String) -> Unit, onOk: () -> Unit) =
        run(onError, onOk) { c.investments.updateTerms(id, name, rateBp, mode, term) }

    fun deleteEnded(onError: (String) -> Unit, onOk: () -> Unit) = run(onError, onOk) { c.investments.deleteEnded(id) }
}

private enum class Dialog { None, TopUp, Redeem, Edit }

@Composable
fun InvestDetailScreen(id: Long, onBack: () -> Unit, onNewContract: (Long) -> Unit, onContract: (Long) -> Unit) {
    val vm = appViewModel(key = "inv$id") { InvestDetailViewModel(it, id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val contractRepo = appContainer().contracts
    val contracts by remember(id) { contractRepo.forInvestment(id) }.collectAsStateWithLifecycle(emptyList())
    var dialog by rememberSaveable { mutableStateOf(Dialog.None) }

    Column(Modifier.fillMaxSize()) {
        val s = state
        PageHeader(s?.snap?.name ?: "投资详情", onBack = onBack)
        if (s == null) return@Column
        val snap = s.snap
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Column(Modifier.enterAnim(0)) {
                MText(if (snap.isActive) "当前总价值" else "赎回金额", style = T.label, color = M.c.inkSoft)
                Spacer(Modifier.height(6.dp))
                MText("¥ " + Fmt.exactYuan(snap.valueExact, if (snap.isActive) 4 else 2), style = T.display.copy(fontSize = 30.sp))
                Spacer(Modifier.height(4.dp))
                MText(
                    "累计收益 " + (if (snap.profitExact.signum() >= 0) "+" else "") + Fmt.exactYuan(snap.profitExact, if (snap.isActive) 4 else 2),
                    style = T.body, color = M.c.accent
                )
            }
            Spacer(Modifier.height(28.dp))
            Column(Modifier.enterAnim(1)) { ProfitChart(s.series, s.now.toEpochSecond(ZoneOffset.UTC)) }
            Spacer(Modifier.height(28.dp))
            Column(Modifier.enterAnim(2)) {
                Fact("本金（含追加）", "¥ " + Money.format(snap.principal))
                Fact("年利率", Percent.format(snap.rateBp))
                Fact("计息方式", snap.mode.label)
                Fact("起始日期", snap.startDate.toString())
                Fact("期限", Fmt.termText(snap.termMonths) + (snap.maturity?.let { "（到期 $it）" } ?: ""))
                Fact(
                    if (!snap.isActive) "最终收益" else if (snap.projectionIsMaturity) "预计到期收益" else "预计一年收益",
                    "¥ " + Money.format(snap.projectedProfit, signed = true)
                )
                MText(snap.mode.explain, Modifier.padding(top = 12.dp), T.caption, M.c.inkSoft)
                if (snap.maturity != null && snap.isActive && s.now.isAfter(snap.maturity.atStartOfDay())) {
                    MText("已到期，利息已停止累计；赎回后转入账户。", Modifier.padding(top = 8.dp), T.caption, M.c.inkSoft)
                }
            }
            Spacer(Modifier.height(28.dp))
            SectionLabel("投入记录")
            snap.tranches.forEachIndexed { i, t ->
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    MText(t.date.toString() + if (i == 0) "  初始本金" else "  追加", Modifier.weight(1f), T.body, M.c.inkSoft)
                    MText(Money.format(t.amount), style = T.amount)
                }
                Hairline()
            }
            Spacer(Modifier.height(28.dp))
            SectionLabel("合同")
            contracts.forEach { k ->
                Column(Modifier.fillMaxWidth().pressable { onContract(k.id) }) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                        MText(k.partyAName + " ↔ " + k.partyBName, Modifier.weight(1f), T.body)
                        MText(if (k.sealed) "已签署" else "待签名", style = T.caption, color = if (k.sealed) M.c.accent else M.c.inkSoft)
                    }
                    Hairline()
                }
            }
            Spacer(Modifier.height(12.dp))
            MButton("生成投资合同", { onNewContract(id) }, Modifier.fillMaxWidth(), ButtonKind.Ghost)
            Spacer(Modifier.height(28.dp))
            if (snap.isActive) {
                MButton("追加投资", { dialog = Dialog.TopUp }, Modifier.fillMaxWidth(), ButtonKind.Ghost)
                Spacer(Modifier.height(12.dp))
                MButton("修改利率 / 期限", { dialog = Dialog.Edit }, Modifier.fillMaxWidth(), ButtonKind.Ghost)
                Spacer(Modifier.height(12.dp))
                MButton("赎回 / 结束投资", { dialog = Dialog.Redeem }, Modifier.fillMaxWidth())
            } else {
                MText("已于 ${snap.endAt?.toLocalDate()} 赎回", style = T.caption, color = M.c.inkSoft)
                Spacer(Modifier.height(12.dp))
                MButton("删除这条记录", { vm.deleteEnded({ toast(it) }) { toast("已删除"); onBack() } }, Modifier.fillMaxWidth(), ButtonKind.Danger)
            }
        }

        when (dialog) {
            Dialog.TopUp -> TopUpDialog(s, { dialog = Dialog.None }) { amount, date, src, err ->
                vm.topUp(amount, date, src, err) { toast("已追加"); dialog = Dialog.None }
            }
            Dialog.Redeem -> RedeemDialog(s, { dialog = Dialog.None }) { acc, err ->
                vm.redeem(acc, err) { amt -> toast("已转入 ¥" + Money.format(amt)); dialog = Dialog.None }
            }
            Dialog.Edit -> EditTermsDialog(snap, { dialog = Dialog.None }) { n, r, m, t, err ->
                vm.updateTerms(n, r, m, t, err) { toast("已更新"); dialog = Dialog.None }
            }
            Dialog.None -> Unit
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        MText(label, Modifier.weight(1f), T.body, M.c.inkSoft)
        MText(value, style = T.body.copy(fontFeatureSettings = "tnum"))
    }
}

@Composable
private fun TopUpDialog(
    s: InvestDetailState, onDismiss: () -> Unit,
    onSubmit: (Long, LocalDate, Long?, (String) -> Unit) -> Unit
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var dayEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var source by rememberSaveable { mutableLongStateOf(s.accounts.firstOrNull()?.account?.id ?: -2L) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    MDialog("追加投资", onDismiss, "确定", {
        when (val a = Money.parsePositive(amount)) {
            is Parsed.Err -> error = a.message
            is Parsed.Ok -> onSubmit(a.value, LocalDate.ofEpochDay(dayEpoch), source.takeIf { it >= 0 }) { error = it }
        }
    }) {
        AmountField(amount, { amount = it; error = null }, style = T.display.copy(fontSize = 26.sp), label = "追加金额")
        Spacer(Modifier.height(12.dp))
        DatePickerRow("追加日期", LocalDate.ofEpochDay(dayEpoch), { dayEpoch = it.toEpochDay() }, min = s.snap.startDate, max = LocalDate.now())
        Spacer(Modifier.height(12.dp))
        SectionLabel("资金来源")
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            s.accounts.forEach { Chip(it.account.name, source == it.account.id, { source = it.account.id }) }
            Chip("不扣款", source == -2L, { source = -2L })
        }
        ErrorText(error)
    }
}

@Composable
private fun RedeemDialog(s: InvestDetailState, onDismiss: () -> Unit, onSubmit: (Long, (String) -> Unit) -> Unit) {
    var account by rememberSaveable { mutableLongStateOf(s.accounts.firstOrNull()?.account?.id ?: -1L) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    MDialog("赎回 / 结束投资", onDismiss, "确认赎回", {
        if (account < 0) error = "请选择到账账户" else onSubmit(account) { error = it }
    }) {
        MText("将赎回本金与收益共", style = T.caption, color = M.c.inkSoft)
        MText("¥ " + Money.format(s.snap.value), Modifier.padding(vertical = 6.dp), T.display.copy(fontSize = 28.sp))
        MText("本金 " + Money.format(s.snap.principal) + "　收益 " + Money.format(s.snap.profit, signed = true), style = T.caption, color = M.c.inkSoft)
        Spacer(Modifier.height(16.dp))
        SectionLabel("转入账户")
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)
        ) { s.accounts.forEach { Chip(it.account.name, account == it.account.id, { account = it.account.id }) } }
        MText("赎回后投资结束，不能再追加。", Modifier.padding(top = 12.dp), T.caption, M.c.inkSoft)
        ErrorText(error)
    }
}

@Composable
private fun EditTermsDialog(
    snap: InvestmentSnapshot, onDismiss: () -> Unit,
    onSubmit: (String, Int, InterestMode, Int?, (String) -> Unit) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(snap.name) }
    var rate by rememberSaveable { mutableStateOf(Percent.toInputText(snap.rateBp)) }
    var modeIdx by rememberSaveable { mutableIntStateOf(snap.mode.ordinal) }
    var term by rememberSaveable { mutableStateOf(snap.termMonths?.toString() ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    MDialog("修改投资条款", onDismiss, "保存", {
        val r = Percent.parse(rate)
        val t = term.toIntOrNull()
        when {
            r is Parsed.Err -> error = r.message
            term.isNotEmpty() && t == null -> error = "期限格式不正确"
            else -> onSubmit(name, (r as Parsed.Ok).value, InterestMode.entries[modeIdx], t) { error = it }
        }
    }) {
        LineField(name, { name = it.take(30) }, "投资对象")
        Spacer(Modifier.height(16.dp))
        LineField(rate, { if (Percent.isTypingValid(it)) rate = it }, "年利率", keyboardType = KeyboardType.Decimal, suffix = "%")
        Spacer(Modifier.height(16.dp))
        Segmented(InterestMode.entries.map { it.label }, modeIdx, { modeIdx = it })
        Spacer(Modifier.height(16.dp))
        LineField(term, { if (it.length <= 3 && it.all(Char::isDigit)) term = it }, "期限（可选）", placeholder = "不设期限", keyboardType = KeyboardType.Number, suffix = "个月")
        MText("修改会对全部本金按新条款重新计算。", Modifier.padding(top = 10.dp), T.caption, M.c.inkSoft)
        ErrorText(error)
    }
}
