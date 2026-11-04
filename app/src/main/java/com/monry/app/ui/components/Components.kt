@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.monry.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.monry.app.data.AccountWithBalance
import com.monry.app.domain.Money
import com.monry.app.ui.theme.LocalReduceMotion
import com.monry.app.ui.theme.M
import com.monry.app.ui.theme.T
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Shows a short message; provided by the root scaffold. */
val LocalToast = compositionLocalOf<(String) -> Unit> { {} }

@Composable
fun MText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = T.body,
    color: Color = M.c.ink,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null
) = Text(
    text, modifier = modifier, style = style, color = color, maxLines = maxLines, textAlign = textAlign,
    overflow = if (maxLines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis
)

@Composable
fun PageHeader(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            MText("‹", Modifier.pressable(onClick = onBack).padding(end = 16.dp, top = 2.dp, bottom = 2.dp), T.display.copy(fontSize = 30.sp))
        }
        MText(title, Modifier.weight(1f), T.title)
        trailing()
    }
}

@Composable
fun TextAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = M.c.inkSoft) {
    MText(text, modifier.pressable(onClick = onClick).padding(vertical = 6.dp, horizontal = 4.dp), T.label, color)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    MText(text, modifier.padding(bottom = 10.dp), T.label, M.c.inkSoft)
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(M.c.line))
}

@Composable
fun ErrorText(message: String?, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        MText(message ?: "", Modifier.padding(top = 8.dp), T.caption.copy(fontSize = 13.sp), M.c.danger)
    }
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    // Nested shell + core with concentric radii (26 = 22 + 4 padding), so the card reads as a tray holding a plate.
    val inner = RoundedCornerShape(22.dp)
    Box(modifier.clip(RoundedCornerShape(26.dp)).background(M.c.surfaceAlt.copy(alpha = 0.55f)).padding(4.dp)) {
        Box(Modifier.fillMaxWidth().clip(inner).background(M.c.surface).border(BorderStroke(1.dp, M.c.line), inner)) { content() }
    }
}

enum class ButtonKind { Primary, Ghost, Danger }

@Composable
fun MButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true
) {
    val bg = when (kind) {
        ButtonKind.Primary -> M.c.ink
        ButtonKind.Ghost -> Color.Transparent
        ButtonKind.Danger -> Color.Transparent
    }
    val fg = when (kind) {
        ButtonKind.Primary -> M.c.bg
        ButtonKind.Ghost -> M.c.ink
        ButtonKind.Danger -> M.c.danger
    }
    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier.height(54.dp).clip(shape).background(bg)
            .let { if (kind == ButtonKind.Primary) it else it.border(1.dp, if (kind == ButtonKind.Danger) M.c.danger.copy(alpha = .4f) else M.c.line, shape) }
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        MText(text, style = T.headline, color = if (enabled) fg else fg.copy(alpha = .4f))
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val spec = if (reduce) snap() else tween<Color>(220)
    val bg by animateColorAsState(if (selected) M.c.ink else M.c.surface, spec, label = "chipBg")
    val fg by animateColorAsState(if (selected) M.c.bg else M.c.inkSoft, spec, label = "chipFg")
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier.clip(shape).background(bg).border(1.dp, if (selected) bg else M.c.line, shape)
            .pressable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp)
    ) { MText(text, style = T.label, color = fg, maxLines = 1) }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    BoxWithConstraints(
        modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(21.dp)).background(M.c.surfaceAlt)
    ) {
        val seg = maxWidth / options.size
        val x by animateDpAsState(seg * selected, if (reduce) snap() else spring(0.8f, 420f), label = "seg")
        Box(
            Modifier.offset(x = x).width(seg).fillMaxHeight().padding(3.dp)
                .clip(RoundedCornerShape(18.dp)).background(M.c.surface)
        )
        Row(Modifier.fillMaxSize()) {
            options.forEachIndexed { i, label ->
                Box(Modifier.weight(1f).fillMaxHeight().pressable { onSelect(i) }, contentAlignment = Alignment.Center) {
                    MText(label, style = T.label, color = if (i == selected) M.c.ink else M.c.inkSoft)
                }
            }
        }
    }
}

/** Underlined single-line text field. */
@Composable
fun LineField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    suffix: String? = null
) {
    Column(modifier.fillMaxWidth()) {
        MText(label, style = T.caption, color = M.c.inkSoft)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) MText(placeholder, style = T.body.copy(fontSize = 17.sp), color = M.c.inkFaint)
                BasicTextField(
                    value = value, onValueChange = onChange, singleLine = true,
                    textStyle = T.body.copy(fontSize = 17.sp, color = M.c.ink),
                    cursorBrush = SolidColor(M.c.ink),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (suffix != null) MText(suffix, style = T.body, color = M.c.inkSoft)
        }
        Spacer(Modifier.height(8.dp))
        Hairline()
    }
}

/** Decimal money input; characters that would make the amount invalid are simply not accepted. */
@Composable
fun AmountField(
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = T.hero,
    label: String? = null,
    placeholder: String = "0.00",
    prefix: String = "¥"
) {
    Column(modifier.fillMaxWidth()) {
        if (label != null) MText(label, style = T.caption, color = M.c.inkSoft)
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            MText(prefix, style = style.copy(color = M.c.inkFaint), color = M.c.inkFaint)
            Spacer(Modifier.width(6.dp))
            Box(Modifier.weight(1f)) {
                if (value.isEmpty()) MText(placeholder, style = style, color = M.c.inkFaint.copy(alpha = .6f))
                BasicTextField(
                    value = value,
                    onValueChange = { if (Money.isTypingValid(it)) onChange(it) },
                    singleLine = true,
                    textStyle = style.copy(color = M.c.ink),
                    cursorBrush = SolidColor(M.c.ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Hairline()
    }
}

@Composable
fun PickerRow(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().pressable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            MText(label, Modifier.weight(1f), T.body, M.c.inkSoft)
            MText(value, style = T.body.copy(fontFeatureSettings = "tnum"))
            MText("  ›", style = T.body, color = M.c.inkFaint)
        }
        Hairline()
    }
}

@Composable
fun DatePickerRow(label: String, date: LocalDate, onPick: (LocalDate) -> Unit, modifier: Modifier = Modifier, min: LocalDate? = null, max: LocalDate? = null) {
    var open by remember { mutableStateOf(false) }
    PickerRow(label, date.toString(), { open = true }, modifier)
    if (open) DatePickerDialogM(date, min, max, onDismiss = { open = false }, onPick = onPick)
}

@Composable
fun TimePickerRow(label: String, time: LocalTime, onPick: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    PickerRow(label, "%02d:%02d".format(time.hour, time.minute), { open = true }, modifier)
    if (open) {
        val state = rememberTimePickerState(time.hour, time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            containerColor = M.c.surface,
            confirmButton = {
                TextButton({ onPick(LocalTime.of(state.hour, state.minute)); open = false }) { MText("确定", style = T.label) }
            },
            dismissButton = { TextButton({ open = false }) { MText("取消", style = T.label, color = M.c.inkSoft) } },
            text = { TimePicker(state = state) }
        )
    }
}

@Composable
fun DatePickerDialogM(initial: LocalDate, min: LocalDate?, max: LocalDate?, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    fun toDate(utcMillis: Long) = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val d = toDate(utcTimeMillis)
                return (min == null || !d.isBefore(min)) && (max == null || !d.isAfter(max))
            }
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton({ state.selectedDateMillis?.let { onPick(toDate(it)) }; onDismiss() }) { MText("确定", style = T.label) }
        },
        dismissButton = { TextButton(onDismiss) { MText("取消", style = T.label, color = M.c.inkSoft) } }
    ) { DatePicker(state = state) }
}

@Composable
fun AccountChips(accounts: List<AccountWithBalance>, selectedId: Long?, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        accounts.forEach { Chip(it.account.name, it.account.id == selectedId, { onSelect(it.account.id) }) }
    }
}

@Composable
fun ChipFlow(items: List<String>, selected: String?, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { Chip(it, it == selected, { onSelect(it) }) }
    }
}

@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
        MText(text, style = T.body, color = M.c.inkFaint, textAlign = TextAlign.Center)
    }
}

@Composable
fun MDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    dismissText: String = "取消",
    content: @Composable () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = M.c.surface,
        shape = RoundedCornerShape(28.dp),
        title = { MText(title, style = T.headline) },
        text = { Column(modifier.verticalScroll(rememberScrollState())) { content() } },
        confirmButton = { TextButton(onConfirm) { MText(confirmText, style = T.label) } },
        dismissButton = { TextButton(onDismiss) { MText(dismissText, style = T.label, color = M.c.inkSoft) } }
    )
}

val ScreenPadding = PaddingValues(horizontal = 24.dp)
