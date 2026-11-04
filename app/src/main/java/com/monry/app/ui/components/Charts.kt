package com.monry.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.monry.app.domain.Bucket
import com.monry.app.domain.Money
import com.monry.app.domain.SeriesPoint
import com.monry.app.ui.theme.M
import com.monry.app.ui.theme.T
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.math.roundToInt

/** Cumulative-profit line. Solid up to "now", dashed for the projection; drag to read values. */
@Composable
fun ProfitChart(points: List<SeriesPoint>, nowSecond: Long, modifier: Modifier = Modifier) {
    val ink = M.c.ink
    val accent = M.c.accent
    val line = M.c.line
    val progress = rememberGrow(1f)
    var scrub by remember { mutableStateOf<Int?>(null) }
    val shown = scrub?.let { points.getOrNull(it) }

    Column(modifier.fillMaxWidth()) {
        MText(
            if (shown != null) {
                val at = LocalDateTime.ofEpochSecond(shown.epochSecond, 0, ZoneOffset.UTC).toLocalDate()
                "$at   累计收益 ¥" + Money.format(shown.profitMinor)
            } else "拖动查看任意时点的累计收益",
            style = T.caption, color = M.c.inkSoft
        )
        Canvas(
            Modifier.fillMaxWidth().height(170.dp).padding(top = 12.dp)
                .pointerInput(points.size) {
                    fun indexAt(x: Float) = ((x / size.width) * (points.size - 1)).roundToInt().coerceIn(0, points.size - 1)
                    detectHorizontalDragGestures(
                        onDragStart = { scrub = indexAt(it.x) },
                        onDragEnd = { scrub = null },
                        onDragCancel = { scrub = null },
                        onHorizontalDrag = { change, _ -> scrub = indexAt(change.position.x) }
                    )
                }
        ) {
            val n = points.size
            if (n < 2) return@Canvas
            val minV = minOf(0L, points.minOf { it.profitMinor }).toFloat()
            var maxV = points.maxOf { it.profitMinor }.toFloat()
            if (maxV - minV < 1f) maxV = minV + 1f
            val padV = 8.dp.toPx()
            fun px(i: Int) = size.width * i / (n - 1)
            fun py(v: Long) = padV + (size.height - 2 * padV) * (1f - (v - minV) / (maxV - minV))

            val baseline = py(0)
            drawLine(line, Offset(0f, baseline), Offset(size.width, baseline), 1.dp.toPx())

            val nowIdx = points.indexOfLast { it.epochSecond <= nowSecond }.coerceAtLeast(0)
            val solid = Path().apply {
                moveTo(px(0), py(points[0].profitMinor))
                for (i in 1..nowIdx) lineTo(px(i), py(points[i].profitMinor))
            }
            if (nowIdx > 0) {
                val area = Path().apply {
                    addPath(solid)
                    lineTo(px(nowIdx), baseline)
                    lineTo(px(0), baseline)
                    close()
                }
                drawPath(area, Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f * progress), Color.Transparent)))
                val measure = PathMeasure().apply { setPath(solid, false) }
                val part = Path()
                measure.getSegment(0f, measure.length * progress, part, true)
                drawPath(part, ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (nowIdx < n - 1 && progress > 0.98f) {
                val future = Path().apply {
                    moveTo(px(nowIdx), py(points[nowIdx].profitMinor))
                    for (i in nowIdx + 1 until n) lineTo(px(i), py(points[i].profitMinor))
                }
                drawPath(
                    future, ink.copy(alpha = 0.45f),
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                )
            }
            scrub?.let { i ->
                val x = px(i)
                drawLine(ink.copy(alpha = 0.25f), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                drawCircle(ink, 4.dp.toPx(), Offset(x, py(points[i].profitMinor)))
            }
        }
    }
}

/** One bar per bucket (day of month / month of year). Tap a bar to read its amount. */
@Composable
fun BucketBars(buckets: List<Bucket>, showIncome: Boolean, modifier: Modifier = Modifier) {
    val ink = M.c.ink
    val accent = M.c.accent
    val track = M.c.surfaceAlt
    val grow = rememberGrow(1f)
    var picked by remember { mutableStateOf<Int?>(null) }
    val values = buckets.map { if (showIncome) it.income else it.expense }
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)

    Column(modifier.fillMaxWidth()) {
        MText(
            picked?.let { buckets.getOrNull(it) }?.let { "${it.label}　¥" + Money.format(if (showIncome) it.income else it.expense) } ?: "点按柱子查看金额",
            style = T.caption, color = M.c.inkSoft
        )
        Canvas(
            Modifier.fillMaxWidth().height(120.dp).padding(top = 10.dp)
                .pointerInput(buckets.size) {
                    detectTapGestures { o ->
                        picked = ((o.x / size.width) * buckets.size).toInt().coerceIn(0, buckets.size - 1)
                    }
                }
        ) {
            val n = buckets.size
            if (n == 0) return@Canvas
            val slot = size.width / n
            val barW = (slot * 0.56f).coerceAtMost(14.dp.toPx())
            values.forEachIndexed { i, v ->
                val x = slot * i + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), androidx.compose.ui.geometry.CornerRadius(barW / 2))
                val h = (size.height * (v.toFloat() / max) * grow).coerceAtLeast(if (v > 0) barW else 0f)
                if (v > 0) {
                    drawRoundRect(
                        if (picked == i) accent else ink, Offset(x, size.height - h), Size(barW, h),
                        androidx.compose.ui.geometry.CornerRadius(barW / 2)
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            val step = if (buckets.size > 12) 5 else 1
            buckets.forEachIndexed { i, b ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    if (i == 0 || (i + 1) % step == 0) {
                        MText(b.label, Modifier.wrapContentWidth(unbounded = true), T.caption, M.c.inkFaint, maxLines = 1)
                    }
                }
            }
        }
    }
}
