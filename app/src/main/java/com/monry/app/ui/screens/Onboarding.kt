package com.monry.app.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.monry.app.data.UserError
import com.monry.app.domain.AccountType
import com.monry.app.domain.Money
import com.monry.app.domain.Parsed
import com.monry.app.ui.appContainer
import com.monry.app.ui.components.AmountField
import com.monry.app.ui.components.ErrorText
import com.monry.app.ui.components.MButton
import com.monry.app.ui.components.MText
import com.monry.app.ui.components.RollingMoney
import com.monry.app.ui.components.enterAnim
import com.monry.app.ui.theme.M
import com.monry.app.ui.theme.T
import kotlinx.coroutines.launch

private val PRESETS = listOf("现金" to AccountType.CASH, "银行卡" to AccountType.BANK, "电子钱包" to AccountType.EWALLET)

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val texts = PRESETS.map { rememberSaveable { mutableStateOf("") } }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val parsed = texts.map { Money.parseNonNegative(it.value) }
    val total = parsed.sumOf { (it as? Parsed.Ok)?.value ?: 0L }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(48.dp))
        MText("欢迎使用 Monry", Modifier.enterAnim(0), T.title)
        MText("先告诉我你现在有多少钱。可以只填一部分，之后随时在「设置」中修改。", Modifier.padding(top = 10.dp).enterAnim(1), T.body, M.c.inkSoft)
        Spacer(Modifier.height(36.dp))
        PRESETS.forEachIndexed { i, (label, _) ->
            AmountField(texts[i].value, { texts[i].value = it; error = null }, Modifier.enterAnim(i + 2), style = T.display.copy(fontSize = 26.sp), label = label)
            Spacer(Modifier.height(24.dp))
        }
        MText("当前总资产", style = T.caption, color = M.c.inkSoft)
        RollingMoney(total, T.display, prefix = "¥ ")
        ErrorText(error)
        Spacer(Modifier.height(32.dp))
        MButton("开始记账", {
            val bad = parsed.firstOrNull { it is Parsed.Err } as? Parsed.Err
            if (bad != null) {
                error = bad.message
            } else {
                scope.launch {
                    try {
                        c.ledger.createAccounts(PRESETS.mapIndexed { i, (n, t) -> Triple(n, t, (parsed[i] as Parsed.Ok).value) })
                        c.onboarded = true
                        onDone()
                    } catch (e: UserError) {
                        error = e.message
                    }
                }
            }
        }, Modifier.fillMaxWidth())
        Spacer(Modifier.navigationBarsPadding().height(32.dp))
    }
}
