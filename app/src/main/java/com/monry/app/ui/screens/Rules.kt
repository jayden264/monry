package com.monry.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.monry.app.AppContainer
import com.monry.app.data.AccountWithBalance
import com.monry.app.data.RecurringRuleEntity
import com.monry.app.data.RuleDraft
import com.monry.app.data.RuleExecutionEntity
import com.monry.app.data.UserError
import com.monry.app.data.schedule
import com.monry.app.domain.Categories
import com.monry.app.domain.Money
import com.monry.app.domain.Recurrence
import com.monry.app.domain.RuleStatus
import com.monry.app.domain.TxType
import com.monry.app.ui.Fmt
import com.monry.app.ui.appViewModel
import com.monry.app.ui.clock
import com.monry.app.ui.components.AccountChips
import com.monry.app.ui.components.AmountField
import com.monry.app.ui.components.ButtonKind
import com.monry.app.ui.components.Chip
import com.monry.app.ui.components.ChipFlow
import com.monry.app.ui.components.DatePickerRow
import com.monry.app.ui.components.EmptyHint
import com.monry.app.ui.components.ErrorText
import com.monry.app.ui.components.Hairline
import com.monry.app.ui.components.LineField
import com.monry.app.ui.components.LocalToast
import com.monry.app.ui.components.MButton
import com.monry.app.ui.components.MText
import com.monry.app.ui.components.PageHeader
import com.monry.app.ui.components.SectionLabel
import com.monry.app.ui.components.Segmented
import com.monry.app.ui.components.SurfaceCard
import com.monry.app.ui.components.TextAction
import com.monry.app.ui.components.TimePickerRow
import com.monry.app.ui.components.enterAnim
import com.monry.app.ui.components.pressable
import com.monry.app.ui.theme.M
import com.monry.app.ui.theme.T
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

data class RulesState(val rules: List<RecurringRuleEntity>, val accountNames: Map<Long, String>)

private fun launchSafely(vm: ViewModel, onError: (String) -> Unit, block: suspend () -> Unit) {
    vm.viewModelScope.launch {
        try {
            block()
        } catch (e: UserError) {
            onError(e.message ?: "操作失败")
        }
    }
}

class RulesViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<RulesState?> = combine(c.recurring.rules(), c.ledger.accounts) { rules, accounts ->
        RulesState(rules, accounts.associate { it.account.id to it.account.name })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun togglePause(rule: RecurringRuleEntity, onError: (String) -> Unit) = launchSafely(this, onError) {
        c.recurring.setPaused(rule.id, paused = rule.status == RuleStatus.ACTIVE, now = LocalDateTime.now())
    }
}

@Composable
fun RulesScreen(onNew: () -> Unit, onEdit: (Long) -> Unit, onHistory: (Long) -> Unit) {
    val vm = appViewModel { RulesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    Column(Modifier.fillMaxSize()) {
        PageHeader("定时") { TextAction("＋ 新建", onNew, color = M.c.ink) }
        val s = state ?: return@Column
        if (s.rules.isEmpty()) {
            EmptyHint("还没有定时规则\n工资、房租、订阅……设置一次，到期自动入账或扣账")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    MText(
                        "到期自动生成流水；关机或未运行期间错过的，会在下次启动时补记，且不会重复。",
                        style = T.caption, color = M.c.inkSoft
                    )
                }
                itemsIndexed(s.rules, key = { _, r -> r.id }) { i, r ->
                    RuleCard(r, s.accountNames[r.accountId], Modifier.enterAnim(i),
                        onClick = { onEdit(r.id) }, onHistory = { onHistory(r.id) },
                        onToggle = { vm.togglePause(r) { toast(it) } })
                }
            }
        }
    }
}

@Composable
private fun RuleCard(
    r: RecurringRuleEntity, accountName: String?, modifier: Modifier,
    onClick: () -> Unit, onHistory: () -> Unit, onToggle: () -> Unit
) {
    val schedule = r.schedule()
    SurfaceCard(modifier.fillMaxWidth().pressable(onClick = onClick)) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MText(r.name, Modifier.weight(1f), T.headline, maxLines = 1)
                MText(
                    (if (r.type == TxType.INCOME) "+" else "-") + Money.format(r.amount),
                    style = T.amount, color = if (r.type == TxType.INCOME) M.c.accent else M.c.ink
                )
            }
            Spacer(Modifier.height(6.dp))
            MText(Fmt.recurrence(schedule.start, r.recurrence), style = T.caption, color = M.c.inkSoft)
            MText(
                listOfNotNull(accountName, r.category).joinToString(" · "),
                style = T.caption, color = M.c.inkSoft
            )
            Spacer(Modifier.height(10.dp))
            Hairline()
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                val nextText = when {
                    r.status == RuleStatus.PAUSED -> "已暂停"
                    r.status == RuleStatus.COMPLETED || !schedule.hasOccurrence(r.nextIndex) -> "已完成"
                    else -> "下次 " + Fmt.dateTime(schedule.occurrence(r.nextIndex))
                }
                MText(nextText, Modifier.weight(1f), T.caption, if (r.status == RuleStatus.ACTIVE) M.c.ink else M.c.inkFaint)
                TextAction("记录", onHistory)
                if (r.status != RuleStatus.COMPLETED) {
                    TextAction(if (r.status == RuleStatus.ACTIVE) "暂停" else "恢复", onToggle)
                }
            }
        }
    }
}

class RuleEditViewModel(private val c: AppContainer) : ViewModel() {
    val accounts: StateFlow<List<AccountWithBalance>> =
        c.ledger.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun load(id: Long) = c.recurring.rule(id)

    fun save(id: Long?, draft: RuleDraft, onDone: () -> Unit, onError: (String) -> Unit) =
        launchSafely(this, onError) {
            c.recurring.save(id, draft, LocalDateTime.now())
            onDone()
        }

    fun delete(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            c.recurring.delete(id)
            onDone()
        }
    }
}

@Composable
fun RuleEditScreen(ruleId: Long, onBack: () -> Unit) {
    val vm = appViewModel { RuleEditViewModel(it) }
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    val editing = ruleId != -1L
    val suggested = remember { LocalDateTime.now().plusHours(1).withMinute(0).withSecond(0).withNano(0) }

    var name by rememberSaveable { mutableStateOf("") }
    var typeIdx by rememberSaveable { mutableIntStateOf(0) } // 0 = deduct, 1 = credit
    var amountText by rememberSaveable { mutableStateOf("") }
    var accountId by rememberSaveable { mutableLongStateOf(-1L) }
    var category by rememberSaveable { mutableStateOf("其他") }
    var dayEpoch by rememberSaveable { mutableLongStateOf(suggested.toLocalDate().toEpochDay()) }
    var minuteOfDay by rememberSaveable { mutableIntStateOf(suggested.hour * 60) }
    var recIdx by rememberSaveable { mutableIntStateOf(Recurrence.MONTHLY.ordinal) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val type = if (typeIdx == 0) TxType.EXPENSE else TxType.INCOME
    val recurrence = Recurrence.entries[recIdx]

    LaunchedEffect(ruleId) {
        if (editing) vm.load(ruleId)?.let {
            name = it.name
            typeIdx = if (it.type == TxType.EXPENSE) 0 else 1
            amountText = Money.toInputText(it.amount)
            accountId = it.accountId
            category = it.category
            dayEpoch = it.startEpochDay
            minuteOfDay = it.startMinuteOfDay
            recIdx = it.recurrence.ordinal
        }
    }
    LaunchedEffect(accounts) {
        if (accountId == -1L || accounts.none { it.account.id == accountId }) {
            accounts.firstOrNull()?.let { accountId = it.account.id }
        }
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        PageHeader(if (editing) "编辑规则" else "新建规则", onBack = onBack)
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            LineField(name, { name = it.take(30); error = null }, "规则名称", placeholder = "例如：房租、工资")
            Spacer(Modifier.height(24.dp))
            Segmented(listOf("扣账", "进账"), typeIdx, {
                typeIdx = it
                category = Categories.of(if (it == 0) TxType.EXPENSE else TxType.INCOME).last()
            })
            Spacer(Modifier.height(24.dp))
            AmountField(amountText, { amountText = it; error = null }, style = T.display, label = "金额")
            Spacer(Modifier.height(24.dp))
            SectionLabel("目标账户")
            AccountChips(accounts, accountId, { accountId = it })
            Spacer(Modifier.height(24.dp))
            SectionLabel("分类")
            ChipFlow(Categories.of(type), category, { category = it })
            Spacer(Modifier.height(20.dp))
            SectionLabel("重复周期")
            ChipFlow(Recurrence.entries.map { it.label }, recurrence.label, { label ->
                recIdx = Recurrence.entries.indexOfFirst { it.label == label }
            })
            Spacer(Modifier.height(8.dp))
            DatePickerRow(if (recurrence == Recurrence.ONCE) "执行日期" else "首次执行日期", LocalDate.ofEpochDay(dayEpoch), { dayEpoch = it.toEpochDay() })
            TimePickerRow("执行时间", LocalTime.of(minuteOfDay / 60, minuteOfDay % 60), { minuteOfDay = it.hour * 60 + it.minute })
            val day = LocalDate.ofEpochDay(dayEpoch).dayOfMonth
            if (recurrence == Recurrence.MONTHLY && day > 28) {
                MText("没有 $day 日的月份，会在当月最后一天执行。", Modifier.padding(top = 10.dp), T.caption, M.c.inkSoft)
            }
            if (recurrence == Recurrence.YEARLY && LocalDate.ofEpochDay(dayEpoch).let { it.monthValue == 2 && it.dayOfMonth == 29 }) {
                MText("非闰年会在 2 月 28 日执行。", Modifier.padding(top = 10.dp), T.caption, M.c.inkSoft)
            }
            ErrorText(error)
            Spacer(Modifier.height(32.dp))
            MButton("保存", {
                val draft = RuleDraft(
                    name, Money.parsePositive(amountText), type, accountId.takeIf { it != -1L }, category,
                    LocalDateTime.of(LocalDate.ofEpochDay(dayEpoch), LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)), recurrence
                )
                vm.save(if (editing) ruleId else null, draft, onDone = { toast("已保存"); onBack() }, onError = { error = it })
            }, Modifier.fillMaxWidth())
            if (editing) {
                Spacer(Modifier.height(12.dp))
                if (!confirmDelete) {
                    MButton("删除规则", { confirmDelete = true }, Modifier.fillMaxWidth(), ButtonKind.Danger)
                } else {
                    MButton("再点一次确认删除（已生成的流水会保留）", {
                        vm.delete(ruleId) { toast("已删除"); onBack() }
                    }, Modifier.fillMaxWidth(), ButtonKind.Danger)
                }
            }
        }
    }
}

class RuleHistoryViewModel(c: AppContainer, id: Long) : ViewModel() {
    val rows: StateFlow<List<RuleExecutionEntity>> =
        c.recurring.executions(id).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val name: StateFlow<String> = c.recurring.rules().map { l -> l.firstOrNull { it.id == id }?.name ?: "" }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")
}

@Composable
fun RuleHistoryScreen(ruleId: Long, onBack: () -> Unit) {
    val vm = appViewModel(key = "hist$ruleId") { RuleHistoryViewModel(it, ruleId) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val name by vm.name.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        PageHeader(if (name.isBlank()) "执行记录" else "$name · 执行记录", onBack = onBack)
        if (rows.isEmpty()) {
            EmptyHint("还没有执行记录")
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp)) {
                items(rows, key = { it.id }) { e ->
                    val scheduled = LocalDateTime.of(LocalDate.ofEpochDay(e.scheduledEpochDay), LocalTime.of(e.scheduledMinuteOfDay / 60, e.scheduledMinuteOfDay % 60))
                    val scheduledMs = scheduled.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val late = e.executedAt - scheduledMs > 20 * 60_000L
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                MText(Fmt.dateTime(scheduled), style = T.body)
                                MText(if (late) "补记（应用启动或后台检查时入账）" else "按时执行", style = T.caption, color = if (late) M.c.accent else M.c.inkSoft)
                            }
                            MText(
                                (if (e.type == TxType.INCOME) "+" else "-") + Money.format(e.amount),
                                style = T.amount, color = if (e.type == TxType.INCOME) M.c.accent else M.c.ink
                            )
                        }
                        Hairline()
                    }
                }
            }
        }
    }
}
