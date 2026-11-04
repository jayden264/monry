@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.monry.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.monry.app.AppContainer
import com.monry.app.data.AccountWithBalance
import com.monry.app.data.InvestDraft
import com.monry.app.data.UserError
import com.monry.app.domain.InterestMode
import com.monry.app.domain.InvestTerms
import com.monry.app.domain.InvestmentCalculator
import com.monry.app.domain.Money
import com.monry.app.domain.Parsed
import com.monry.app.domain.Percent
import com.monry.app.domain.Tranche
import com.monry.app.ui.appViewModel
import com.monry.app.ui.components.AmountField
import com.monry.app.ui.components.Chip
import com.monry.app.ui.components.DatePickerRow
import com.monry.app.ui.components.ErrorText
import com.monry.app.ui.components.LineField
import com.monry.app.ui.components.LocalToast
import com.monry.app.ui.components.MButton
import com.monry.app.ui.components.MText
import com.monry.app.ui.components.PageHeader
import com.monry.app.ui.components.SectionLabel
import com.monry.app.ui.components.Segmented
import com.monry.app.ui.theme.M
import com.monry.app.ui.theme.T
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

class InvestNewViewModel(private val c: AppContainer) : ViewModel() {
    val accounts: StateFlow<List<AccountWithBalance>> =
        c.ledger.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(d: InvestDraft, onDone: (Long) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                onDone(c.investments.create(d, LocalDate.now()))
            } catch (e: UserError) {
                onError(e.message ?: "保存失败")
            }
        }
    }
}

private val TERM_PRESETS = listOf("不设期限" to null, "6 个月" to 6, "1 年" to 12, "2 年" to 24, "3 年" to 36, "5 年" to 60)
private const val NO_ACCOUNT = -2L

@Composable
fun InvestNewScreen(onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val vm = appViewModel { InvestNewViewModel(it) }
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val toast = LocalToast.current

    var name by rememberSaveable { mutableStateOf("") }
    var principalText by rememberSaveable { mutableStateOf("") }
    var rateText by rememberSaveable { mutableStateOf("") }
    var modeIdx by rememberSaveable { mutableIntStateOf(0) }
    var dayEpoch by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var termText by rememberSaveable { mutableStateOf("") }
    var sourceId by rememberSaveable { mutableLongStateOf(-1L) } // -1 = not chosen yet, NO_ACCOUNT = do not deduct
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val mode = InterestMode.entries[modeIdx]
    val start = LocalDate.ofEpochDay(dayEpoch)

    LaunchedEffect(accounts) {
        if (sourceId == -1L) accounts.firstOrNull()?.let { sourceId = it.account.id }
    }

    val principal = Money.parsePositive(principalText)
    val rate = Percent.parse(rateText)
    val termMonths = termText.toIntOrNull()

    val preview = remember(principal, rate, mode, start, termMonths) {
        val p = (principal as? Parsed.Ok)?.value
        val r = (rate as? Parsed.Ok)?.value
        if (p == null || r == null) null else {
            val maturity = InvestmentCalculator.maturityDate(start, termMonths?.takeIf { it in 1..600 })
            val terms = InvestTerms(r, mode, maturity)
            val at = (maturity ?: start.plusYears(1)).atStartOfDay()
            (maturity != null) to InvestmentCalculator.profitMinor(listOf(Tranche(start, p)), terms, at)
        }
    }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        PageHeader("新增投资", onBack = onBack)
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            LineField(name, { name = it.take(30); error = null }, "投资对象", placeholder = "例如：某某公司、国债、基金")
            Spacer(Modifier.height(28.dp))
            AmountField(principalText, { principalText = it; error = null }, style = T.display, label = "本金")
            Spacer(Modifier.height(28.dp))
            LineField(
                rateText, { if (Percent.isTypingValid(it)) { rateText = it; error = null } }, "年利率",
                placeholder = "0 – 1000", keyboardType = KeyboardType.Decimal, suffix = "%"
            )
            Spacer(Modifier.height(28.dp))
            SectionLabel("计息方式")
            Segmented(InterestMode.entries.map { it.label }, modeIdx, { modeIdx = it })
            MText(mode.explain, Modifier.padding(top = 10.dp), T.caption, M.c.inkSoft)
            Spacer(Modifier.height(20.dp))
            DatePickerRow("起始日期", start, { dayEpoch = it.toEpochDay() }, max = LocalDate.now())
            Spacer(Modifier.height(16.dp))
            LineField(
                termText, { if (it.length <= 3 && it.all(Char::isDigit)) { termText = it; error = null } }, "期限（可选）",
                placeholder = "不设期限", keyboardType = KeyboardType.Number, suffix = "个月"
            )
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                TERM_PRESETS.forEach { (label, months) ->
                    Chip(label, termMonths == months && (months != null || termText.isEmpty()), { termText = months?.toString() ?: "" })
                }
            }
            Spacer(Modifier.height(28.dp))
            SectionLabel("本金来源")
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
            ) {
                accounts.forEach { Chip(it.account.name, sourceId == it.account.id, { sourceId = it.account.id }) }
                Chip("不从账户扣款", sourceId == NO_ACCOUNT, { sourceId = NO_ACCOUNT })
            }
            val src = accounts.firstOrNull { it.account.id == sourceId }
            val p = (principal as? Parsed.Ok)?.value
            MText(
                when {
                    sourceId == NO_ACCOUNT -> "适合已经在投的项目：只记录，不改动任何账户余额。"
                    src != null && p != null && src.balance < p -> "「${src.account.name}」余额不足，扣款后会变为负数。"
                    else -> "会同时记一笔「投资」支出并扣减该账户余额。"
                },
                Modifier.padding(top = 10.dp), T.caption, M.c.inkSoft
            )
            preview?.let { (isMaturity, profit) ->
                Spacer(Modifier.height(24.dp))
                Row {
                    MText(if (isMaturity) "预计到期收益" else "预计一年收益", Modifier.weight(1f), T.body, M.c.inkSoft)
                    MText("¥ " + Money.format(profit, signed = true), style = T.amount, color = M.c.accent)
                }
            }
            ErrorText(error)
            Spacer(Modifier.height(32.dp))
            MButton("开始投资", {
                val p2 = principal
                val r2 = rate
                when {
                    name.isBlank() -> error = "请输入投资对象名称"
                    p2 is Parsed.Err -> error = p2.message
                    r2 is Parsed.Err -> error = r2.message
                    termText.isNotEmpty() && (termMonths == null || termMonths !in 1..600) -> error = "期限需在 1–600 个月之间"
                    sourceId == -1L -> error = "请选择本金来源"
                    else -> vm.create(
                        InvestDraft(
                            name, (p2 as Parsed.Ok).value, (r2 as Parsed.Ok).value, mode, start,
                            termMonths, sourceId.takeIf { it >= 0 }
                        ),
                        onDone = { toast("已添加投资"); onCreated(it) },
                        onError = { error = it }
                    )
                }
            }, Modifier.fillMaxWidth())
        }
    }
}
