package com.monry.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monry.app.contract.ContractPdf
import com.monry.app.data.UserError
import com.monry.app.data.snapshot
import com.monry.app.data.terms
import com.monry.app.data.verify
import com.monry.app.domain.ContractSeal
import com.monry.app.domain.ContractTerms
import com.monry.app.domain.ContractText
import com.monry.app.domain.Party
import com.monry.app.ui.appContainer
import com.monry.app.ui.components.ButtonKind
import com.monry.app.ui.components.ErrorText
import com.monry.app.ui.components.LineField
import com.monry.app.ui.components.LocalToast
import com.monry.app.ui.components.MButton
import com.monry.app.ui.components.MText
import com.monry.app.ui.components.PageHeader
import com.monry.app.ui.components.SectionLabel
import com.monry.app.ui.components.enterAnim
import com.monry.app.ui.theme.M
import com.monry.app.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset

@Composable
fun ContractNewScreen(investmentId: Long, onBack: () -> Unit, onCreated: (Long) -> Unit) {
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val snap by produceState<com.monry.app.data.InvestmentSnapshot?>(null, investmentId) {
        value = c.investments.observe(investmentId).first()?.snapshot(LocalDateTime.now())
    }
    var aName by rememberSaveable { mutableStateOf("") }
    var aId by rememberSaveable { mutableStateOf("") }
    var bName by rememberSaveable { mutableStateOf("") }
    var bId by rememberSaveable { mutableStateOf("") }
    var payment by rememberSaveable { mutableStateOf("到期一次性支付本金及全部收益") }
    var extra by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())) {
        PageHeader("生成投资合同", onBack = onBack)
        val s = snap
        if (s == null) return@Column
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            MText(
                "以下条款取自投资「${s.name}」：本金 ¥${com.monry.app.domain.Money.format(s.principal)}，" +
                    "年利率 ${com.monry.app.domain.Percent.format(s.rateBp)}，${s.mode.label}。生成后条款不会随投资改动。",
                style = T.caption, color = M.c.inkSoft
            )
            Spacer(Modifier.height(28.dp))
            SectionLabel("甲方（出资方，通常是你）")
            LineField(aName, { aName = it.take(30); error = null }, "姓名", placeholder = "与证件一致")
            Spacer(Modifier.height(16.dp))
            LineField(aId, { aId = it.take(20); error = null }, "证件号", placeholder = "身份证号或护照号")
            Spacer(Modifier.height(28.dp))
            SectionLabel("乙方（接受投资方）")
            LineField(bName, { bName = it.take(30); error = null }, "姓名", placeholder = "与证件一致")
            Spacer(Modifier.height(16.dp))
            LineField(bId, { bId = it.take(20); error = null }, "证件号", placeholder = "身份证号或护照号")
            Spacer(Modifier.height(28.dp))
            LineField(payment, { payment = it.take(200); error = null }, "收益支付方式")
            Spacer(Modifier.height(16.dp))
            LineField(extra, { extra = it.take(1000); error = null }, "补充条款（可选）", placeholder = "例如逾期责任、提前赎回约定")
            ErrorText(error)
            Spacer(Modifier.height(28.dp))
            MText("生成后请双方当面核对条款再签名。签名完成即锁定，不能再改。", style = T.caption, color = M.c.inkSoft)
            Spacer(Modifier.height(16.dp))
            MButton("生成合同", {
                val terms = ContractTerms(
                    s.name, aName, aId, bName, bId, s.principal, s.rateBp, s.mode, s.startDate, s.termMonths, payment, extra
                )
                scope.launch {
                    try {
                        val id = c.contracts.create(investmentId, terms)
                        toast("合同已生成")
                        onCreated(id)
                    } catch (e: UserError) {
                        error = e.message
                    }
                }
            }, Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun ContractScreen(id: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val c = appContainer()
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val contract by remember(id) { c.contracts.observe(id) }.collectAsStateWithLifecycle(null)
    var signing by rememberSaveable { mutableStateOf<Int?>(null) } // 0 = party A, 1 = party B
    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        PageHeader("投资合同", onBack = onBack)
        val k = contract ?: return@Column
        val t = remember(k) { k.terms() }
        val intact = remember(k) { k.verify() }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            MText(ContractText.TITLE, Modifier.fillMaxWidth().enterAnim(0), T.title, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            ContractText.paragraphs(t).forEachIndexed { i, p ->
                MText(p, Modifier.padding(bottom = 14.dp).enterAnim(i + 1), T.body)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                SignBox("甲方 " + k.partyAName, k.sigA, !k.sealed, { signing = 0 }, Modifier.weight(1f))
                Spacer(Modifier.padding(6.dp))
                SignBox("乙方 " + k.partyBName, k.sigB, !k.sealed, { signing = 1 }, Modifier.weight(1f))
            }
            Spacer(Modifier.height(24.dp))
            if (k.sealed) {
                val at = LocalDateTime.ofEpochSecond(k.signedAt!!, 0, ZoneOffset.UTC)
                MText("已签署　${at.toLocalDate()} ${"%02d:%02d".format(at.hour, at.minute)}", style = T.label)
                MText("指纹　" + ContractSeal.grouped(k.fingerprint!!), Modifier.padding(top = 6.dp), T.caption, M.c.inkSoft)
                MText(
                    if (intact) "校验通过：合同内容与签名自签署后未被改动。" else "校验失败：合同内容或签名与签署时不一致！",
                    Modifier.padding(top = 6.dp), T.caption, if (intact) M.c.accent else M.c.danger
                )
                Spacer(Modifier.height(20.dp))
                MButton(if (busy) "正在生成…" else "导出 PDF 并分享", {
                    if (!busy) {
                        busy = true
                        scope.launch {
                            try {
                                val f = withContext(Dispatchers.IO) { ContractPdf.write(ctx, k) }
                                ContractPdf.share(ctx, f)
                            } catch (e: Exception) {
                                toast("导出失败：" + (e.message ?: ""))
                            } finally {
                                busy = false
                            }
                        }
                    }
                }, Modifier.fillMaxWidth())
            } else {
                MText("双方都签名后合同生效并锁定，之后不能再修改。", style = T.caption, color = M.c.inkSoft)
            }
            Spacer(Modifier.height(12.dp))
            MText(
                "提示：手写签名只能证明这份文件在本机生成并未被改动，不等同于经第三方认证的电子签章。金额较大或涉及陌生人时，请另行签订正式合同。",
                style = T.caption, color = M.c.inkFaint
            )
            Spacer(Modifier.height(20.dp))
            MButton(if (confirmDelete) "再点一次确认删除" else "删除合同", {
                if (!confirmDelete) confirmDelete = true else scope.launch {
                    c.contracts.delete(id)
                    toast("已删除")
                    onBack()
                }
            }, Modifier.fillMaxWidth(), ButtonKind.Danger)
        }

        signing?.let { who ->
            SignDialog(
                title = if (who == 0) "甲方 ${k.partyAName} 签名" else "乙方 ${k.partyBName} 签名",
                onDismiss = { signing = null },
                onDone = { png ->
                    scope.launch {
                        try {
                            c.contracts.sign(id, if (who == 0) Party.A else Party.B, png, LocalDateTime.now().toEpochSecond(ZoneOffset.UTC))
                            signing = null
                            toast("已签名")
                        } catch (e: UserError) {
                            toast(e.message ?: "签名失败")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun SignBox(label: String, png: ByteArray?, editable: Boolean, onSign: () -> Unit, modifier: Modifier) {
    val bitmap = remember(png) { png?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    Column(modifier) {
        MText(label, style = T.caption, color = M.c.inkSoft, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(14.dp)).background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
            else MText("待签名", style = T.caption, color = Color(0xFFA8A8AD))
        }
        if (editable) {
            com.monry.app.ui.components.TextAction(if (png == null) "签名" else "重签", onSign, color = M.c.ink)
        }
    }
}

@Composable
private fun SignDialog(title: String, onDismiss: () -> Unit, onDone: (ByteArray) -> Unit) {
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var error by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.padding(16.dp).clip(RoundedCornerShape(28.dp)).background(M.c.surface).padding(24.dp)
        ) {
            MText(title, style = T.headline)
            MText("请在下方框内用手指签名", Modifier.padding(top = 4.dp, bottom = 12.dp), T.caption, M.c.inkSoft)
            Box(Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)) {
                SignaturePad(strokes, { strokes = it; error = null }, Modifier.fillMaxSize().onSizeChanged { size = it })
            }
            ErrorText(error)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                com.monry.app.ui.components.TextAction("清除", { strokes = emptyList() })
                Spacer(Modifier.weight(1f))
                com.monry.app.ui.components.TextAction("取消", onDismiss)
                com.monry.app.ui.components.TextAction("确认签名", {
                    if (strokes.sumOf { it.size } < 12) error = "签名太短，请完整签写"
                    else onDone(renderPng(strokes, size.width, size.height))
                }, color = M.c.ink)
            }
        }
    }
}

@Composable
private fun SignaturePad(strokes: List<List<Offset>>, onChange: (List<List<Offset>>) -> Unit, modifier: Modifier) {
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    val latest by rememberUpdatedState(strokes)
    val change by rememberUpdatedState(onChange)
    Canvas(
        modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { current = listOf(it) },
                onDrag = { pointer, _ ->
                    pointer.consume()
                    current = current + pointer.position
                },
                onDragEnd = {
                    change(latest + listOf(current))
                    current = emptyList()
                },
                onDragCancel = { current = emptyList() }
            )
        }
    ) {
        (strokes + listOf(current)).forEach { s ->
            if (s.size > 1) {
                val path = Path().apply {
                    moveTo(s[0].x, s[0].y)
                    for (i in 1 until s.size) lineTo(s[i].x, s[i].y)
                }
                drawPath(path, Color.Black, style = Stroke(4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

/** Draws the strokes on a transparent bitmap of the pad's size and encodes it as PNG. */
private fun renderPng(strokes: List<List<Offset>>, w: Int, h: Int): ByteArray {
    val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
    }
    strokes.forEach { s ->
        if (s.size > 1) {
            val p = android.graphics.Path()
            p.moveTo(s[0].x, s[0].y)
            for (i in 1 until s.size) p.lineTo(s[i].x, s[i].y)
            canvas.drawPath(p, paint)
        }
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
    return out.toByteArray()
}
