@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.monry.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import android.net.Uri
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.monry.app.AppContainer
import com.monry.app.data.AccountWithBalance
import com.monry.app.data.UserError
import com.monry.app.domain.AccountType
import com.monry.app.domain.Money
import com.monry.app.domain.Parsed
import com.monry.app.ui.appViewModel
import com.monry.app.ui.components.AmountField
import com.monry.app.ui.components.ButtonKind
import com.monry.app.ui.components.Chip
import com.monry.app.ui.components.ErrorText
import com.monry.app.ui.components.Hairline
import com.monry.app.ui.components.LineField
import com.monry.app.ui.components.LocalToast
import com.monry.app.ui.components.MButton
import com.monry.app.ui.components.MDialog
import com.monry.app.ui.components.MText
import com.monry.app.ui.components.PageHeader
import com.monry.app.ui.components.SectionLabel
import com.monry.app.ui.components.TextAction
import com.monry.app.ui.components.pressable
import com.monry.app.ui.theme.M
import com.monry.app.ui.theme.T
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val accounts: StateFlow<List<AccountWithBalance>> =
        c.ledger.accounts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun run(onError: (String) -> Unit, onOk: () -> Unit, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                onOk()
            } catch (e: UserError) {
                onError(e.message ?: "操作失败")
            }
        }
    }

    fun add(name: String, type: AccountType, opening: Long, onError: (String) -> Unit, onOk: () -> Unit) =
        run(onError, onOk) { c.ledger.createAccount(name, type, opening) }

    fun edit(id: Long, name: String, type: AccountType, balance: Long, onError: (String) -> Unit, onOk: () -> Unit) =
        run(onError, onOk) {
            c.ledger.updateAccount(id, name, type)
            c.ledger.setBalance(id, balance)
        }

    fun delete(id: Long, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, onOk) { c.ledger.deleteAccount(id) }

    fun export(open: () -> java.io.OutputStream, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, onOk) {
        try {
            open().use { c.backup.export(it, System.currentTimeMillis()) }
        } catch (e: java.io.IOException) {
            throw UserError("写入文件失败：" + (e.message ?: ""))
        }
    }

    fun import(open: () -> java.io.InputStream, onError: (String) -> Unit, onOk: () -> Unit) = run(onError, onOk) {
        try {
            open().use { c.backup.import(it) }
            c.onboarded = true
        } catch (e: java.io.IOException) {
            throw UserError("读取文件失败：" + (e.message ?: ""))
        }
    }
}

/** accountId == null: create; otherwise edit that account. */
private data class AccountDialog(val account: AccountWithBalance?)

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm = appViewModel { SettingsViewModel(it) }
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val toast = LocalToast.current
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var pendingImport by rememberSaveable { mutableStateOf<String?>(null) }
    val resolver = LocalContext.current.contentResolver
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) vm.export({ resolver.openOutputStream(uri, "wt") ?: throw java.io.IOException("无法打开文件") }, { toast(it) }) { toast("备份已导出") }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) pendingImport = uri.toString()
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader("设置", onBack = onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("账户与资产", Modifier.weight(1f))
                TextAction("＋ 添加", { creating = true }, color = M.c.ink)
            }
            accounts.forEach { a ->
                Column(Modifier.fillMaxWidth().pressable { editingId = a.account.id }) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            MText(a.account.name, style = T.body)
                            MText(a.account.type.label, style = T.caption, color = M.c.inkSoft)
                        }
                        MText(Money.format(a.balance), style = T.amount, color = if (a.balance < 0) M.c.danger else M.c.ink)
                        MText("  ›", style = T.body, color = M.c.inkFaint)
                    }
                    Hairline()
                }
            }
            MText("点按账户可修改名称、类型，或把当前余额直接校准为任意金额。", Modifier.padding(top = 12.dp), T.caption, M.c.inkSoft)
            Spacer(Modifier.height(36.dp))
            SectionLabel("数据备份")
            MText("账户、流水、定时规则和投资都会导出为一个文件。卸载应用会清除本机数据，请定期导出并保存到手机之外的地方（网盘、电脑）。", style = T.caption, color = M.c.inkSoft)
            Spacer(Modifier.height(14.dp))
            MButton("导出备份", {
                val stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                exportLauncher.launch("monry-backup-$stamp.json")
            }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            MButton("从备份恢复", { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, Modifier.fillMaxWidth(), ButtonKind.Ghost)
            Spacer(Modifier.height(36.dp))
            SectionLabel("关于")
            MText("Monry 1.0 · 所有数据仅保存在本机。金额以「分」为单位的整数存储与计算，不使用浮点数。", style = T.caption, color = M.c.inkSoft)
        }
    }

    pendingImport?.let { uriText ->
        MDialog("从备份恢复", { pendingImport = null }, "覆盖并恢复", {
            val uri = Uri.parse(uriText)
            pendingImport = null
            vm.import({ resolver.openInputStream(uri) ?: throw java.io.IOException("无法打开文件") }, { toast(it) }) { toast("已恢复备份") }
        }) {
            MText("这会用备份文件覆盖当前所有数据（账户、流水、规则、投资），无法撤销。建议先导出一份当前备份。", style = T.body, color = M.c.inkSoft)
        }
    }

    if (creating) AccountEditor(null, { creating = false }) { name, type, amount, err ->
        vm.add(name, type, amount, err) { toast("已添加账户"); creating = false }
    }
    editingId?.let { id ->
        accounts.firstOrNull { it.account.id == id }?.let { acc ->
            AccountEditor(acc, { editingId = null }, onDelete = { err -> vm.delete(id, err) { toast("已删除"); editingId = null } }) { name, type, amount, err ->
                vm.edit(id, name, type, amount, err) { toast("已保存"); editingId = null }
            }
        }
    }
}

@Composable
private fun AccountEditor(
    acc: AccountWithBalance?, onDismiss: () -> Unit, onDelete: ((String) -> Unit) -> Unit = {},
    onSubmit: (String, AccountType, Long, (String) -> Unit) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(acc?.account?.name ?: "") }
    var typeIdx by rememberSaveable { mutableIntStateOf((acc?.account?.type ?: AccountType.CASH).ordinal) }
    var amount by rememberSaveable { mutableStateOf(acc?.let { if (it.balance >= 0) Money.toInputText(it.balance) else "" } ?: "") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val negative = (acc?.balance ?: 0) < 0 && amount.isEmpty()
    MDialog(if (acc == null) "添加账户" else "编辑账户", onDismiss, "保存", {
        when (val a = Money.parseNonNegative(amount)) {
            is Parsed.Err -> error = a.message
            is Parsed.Ok -> if (negative && acc != null) onSubmit(name, AccountType.entries[typeIdx], acc.balance) { error = it }
            else onSubmit(name, AccountType.entries[typeIdx], a.value) { error = it }
        }
    }) {
        LineField(name, { name = it.take(20); error = null }, "账户名称", placeholder = "例如：招商银行")
        Spacer(Modifier.height(16.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) { AccountType.entries.forEach { Chip(it.label, typeIdx == it.ordinal, { typeIdx = it.ordinal }) } }
        Spacer(Modifier.height(16.dp))
        AmountField(amount, { amount = it; error = null }, style = T.display.copy(fontSize = 26.sp), label = if (acc == null) "初始金额" else "当前余额", placeholder = if (negative) "当前为负数，留空保持不变" else "0.00")
        ErrorText(error)
        if (acc != null) {
            Spacer(Modifier.height(16.dp))
            MButton("删除账户", { onDelete { error = it } }, Modifier.fillMaxWidth(), ButtonKind.Danger)
        }
    }
}
