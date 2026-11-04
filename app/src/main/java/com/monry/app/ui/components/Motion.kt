package com.monry.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.monry.app.domain.Money
import com.monry.app.ui.theme.LocalReduceMotion
import com.monry.app.ui.theme.M
import kotlinx.coroutines.delay

private val Expo = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)

/** Button feedback: springy press-scale plus a light haptic tick. */
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val reduce = LocalReduceMotion.current
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && !reduce) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
        label = "press"
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }.clickable(interactionSource = source, indication = null, enabled = enabled) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
}

/** Staggered fade-and-rise for list items and sections. */
fun Modifier.enterAnim(index: Int = 0): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) {
            delay(index.coerceIn(0, 8) * 45L)
            progress.animateTo(1f, tween(600, easing = Expo))
        }
    }
    val rise = with(LocalDensity.current) { 28.dp.toPx() }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * rise
        scaleX = 0.985f + 0.015f * p
        scaleY = 0.985f + 0.015f * p
    }
}

/** Items fade in, glide to their new place when the list changes, and fade out on removal. */
@Composable
fun LazyItemScope.itemMotion(): Modifier {
    if (LocalReduceMotion.current) return Modifier
    return Modifier.animateItem(
        fadeInSpec = tween(380, easing = Expo),
        placementSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
        fadeOutSpec = tween(180)
    )
}

/** 0 -> [target] on first composition (bars, progress), then follows changes. */
@Composable
fun rememberGrow(target: Float, delayMs: Int = 0): Float {
    val reduce = LocalReduceMotion.current
    var armed by remember { mutableStateOf(reduce) }
    LaunchedEffect(Unit) { armed = true }
    val v by animateFloatAsState(
        targetValue = if (armed) target else 0f,
        animationSpec = if (reduce) snap() else tween(750, delayMs, Expo),
        label = "grow"
    )
    return v
}

/** Target tilt in degrees; read it to drive highlights that follow the finger. */
@Stable
class TiltState {
    var rx by mutableFloatStateOf(0f)
    var ry by mutableFloatStateOf(0f)
}

/**
 * Tilts the layer towards the finger (perspective rotation) and springs back on release.
 * Touches are observed in the Initial pass and never consumed, so scrolling/clicks still work.
 */
@Composable
fun Modifier.tilt3d(state: TiltState, maxDeg: Float = 7f): Modifier {
    val reduce = LocalReduceMotion.current
    if (reduce) return this
    val rx by animateFloatAsState(state.rx, spring(dampingRatio = 0.5f, stiffness = 200f), label = "rx")
    val ry by animateFloatAsState(state.ry, spring(dampingRatio = 0.5f, stiffness = 200f), label = "ry")
    val density = LocalDensity.current.density
    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                fun follow(p: Offset) {
                    val nx = (p.x / size.width - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
                    val ny = (p.y / size.height - 0.5f).coerceIn(-0.5f, 0.5f) * 2f
                    state.ry = nx * maxDeg
                    state.rx = -ny * maxDeg
                }
                follow(down.position)
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.firstOrNull()?.let { follow(it.position) }
                } while (event.changes.any { it.pressed })
                state.rx = 0f
                state.ry = 0f
            }
        }
        .graphicsLayer {
            rotationX = rx
            rotationY = ry
            cameraDistance = 14f * density
        }
}

/** Counts up/down to [minor] with an expo-out ease; tabular figures keep the width steady. */
@Composable
fun RollingMoney(
    minor: Long,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = M.c.ink,
    signed: Boolean = false,
    prefix: String = ""
) {
    val reduce = LocalReduceMotion.current
    var shown by remember { mutableLongStateOf(if (reduce) minor else 0L) }
    LaunchedEffect(minor, reduce) {
        if (reduce) {
            shown = minor
            return@LaunchedEffect
        }
        val from = shown
        Animatable(0f).animateTo(1f, tween(900, easing = Expo)) {
            shown = from + ((minor - from).toDouble() * value).toLong()
        }
        shown = minor
    }
    Text(prefix + Money.format(shown, signed), style = style, color = color, modifier = modifier, maxLines = 1)
}

/** Cards swing up from a tilted-back pose: a perspective entrance with depth. */
fun Modifier.enterFlip(index: Int = 0): Modifier = composed {
    val reduce = LocalReduceMotion.current
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduce) {
            delay(index.coerceIn(0, 6) * 70L)
            progress.animateTo(1f, tween(780, easing = Expo))
        }
    }
    val density = LocalDensity.current.density
    graphicsLayer {
        val p = progress.value
        alpha = p
        rotationX = (1f - p) * -24f
        translationY = (1f - p) * 40f * density
        scaleX = 0.94f + 0.06f * p
        scaleY = 0.94f + 0.06f * p
        cameraDistance = 14f * density
        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
    }
}
