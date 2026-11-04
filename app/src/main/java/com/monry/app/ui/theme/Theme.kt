package com.monry.app.ui.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
class MonryColors(
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val ink: Color,
    val inkSoft: Color,
    val inkFaint: Color,
    val line: Color,
    val accent: Color,
    val danger: Color,
    val heroBg: Color,
    val heroInk: Color,
    val heroInkSoft: Color,
    val dark: Boolean
)

private val LightColors = MonryColors(
    bg = Color(0xFFFAFAF8), surface = Color(0xFFFFFFFF), surfaceAlt = Color(0xFFF1F1ED),
    ink = Color(0xFF16161A), inkSoft = Color(0xFF6E6E73), inkFaint = Color(0xFFA8A8AD),
    line = Color(0xFFE7E7E2), accent = Color(0xFF1F6A52), danger = Color(0xFFB3412F),
    heroBg = Color(0xFF16161A), heroInk = Color(0xFFFAFAF8), heroInkSoft = Color(0xFF9A9AA0), dark = false
)

private val DarkColors = MonryColors(
    bg = Color(0xFF0E0E10), surface = Color(0xFF17171A), surfaceAlt = Color(0xFF202024),
    ink = Color(0xFFF2F2F0), inkSoft = Color(0xFF9A9AA0), inkFaint = Color(0xFF5E5E64),
    line = Color(0xFF2A2A2E), accent = Color(0xFF6FCF9F), danger = Color(0xFFE8806E),
    heroBg = Color(0xFFECECE8), heroInk = Color(0xFF16161A), heroInkSoft = Color(0xFF6E6E73), dark = true
)

val LocalMonry = staticCompositionLocalOf { LightColors }
val LocalReduceMotion = staticCompositionLocalOf { false }

/** Shorthand: `M.c.ink`. */
object M {
    val c: MonryColors
        @Composable @ReadOnlyComposable get() = LocalMonry.current
}

private val tnum = "tnum"

/** Type scale. Numerals use tabular figures so rolling numbers do not jitter. */
object T {
    val hero = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Light, letterSpacing = (-1.2).sp, fontFeatureSettings = tnum)
    val display = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Light, letterSpacing = (-0.8).sp, fontFeatureSettings = tnum)
    val title = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Medium, letterSpacing = (-0.4).sp)
    val headline = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Medium)
    val body = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal, lineHeight = 21.sp)
    val amount = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = tnum)
    val label = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)
    val caption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp, fontFeatureSettings = tnum)
}

private fun animationsDisabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** True when the user switched off animations system-wide ("Remove animations" / animator scale 0). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    var reduce by remember { mutableStateOf(animationsDisabled(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduce = animationsDisabled(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduce
}

@Composable
fun MonryTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) DarkColors else LightColors
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.ink, onPrimary = colors.bg, background = colors.bg, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink, surfaceVariant = colors.surfaceAlt,
            onSurfaceVariant = colors.inkSoft, outline = colors.line, secondaryContainer = colors.surfaceAlt,
            onSecondaryContainer = colors.ink, surfaceContainerHigh = colors.surface, surfaceContainerHighest = colors.surfaceAlt
        )
    } else {
        lightColorScheme(
            primary = colors.ink, onPrimary = colors.bg, background = colors.bg, onBackground = colors.ink,
            surface = colors.surface, onSurface = colors.ink, surfaceVariant = colors.surfaceAlt,
            onSurfaceVariant = colors.inkSoft, outline = colors.line, secondaryContainer = colors.surfaceAlt,
            onSecondaryContainer = colors.ink, surfaceContainerHigh = colors.surface, surfaceContainerHighest = colors.surfaceAlt
        )
    }
    CompositionLocalProvider(
        LocalMonry provides colors,
        LocalReduceMotion provides rememberReduceMotion()
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
