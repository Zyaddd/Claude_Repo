package com.arabtv.iptv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabtv.iptv.R

// A cinematic dark palette: near-black canvas, layered greys, one warm accent.
val Bg = Color(0xFF07080B)
val Surface1 = Color(0xFF12141A)
val Surface2 = Color(0xFF1C1F27)
val Surface3 = Color(0xFF272B35)
val Gold = Color(0xFFF2C14E)
val Brand = Color(0xFF14A38B)
val TextPrimary = Color(0xFFF5F6F8)
val TextSecondary = Color(0xFFA4ABB8)
val LiveRed = Color(0xFFE5484D)

val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold),
)

private val scheme = darkColorScheme(
    primary = Gold,
    onPrimary = Color(0xFF1F1500),
    primaryContainer = Brand,
    onPrimaryContainer = Color.White,
    secondary = Brand,
    onSecondary = Color.White,
    background = Bg,
    onBackground = TextPrimary,
    surface = Bg,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Surface3,
    surfaceContainerLow = Surface1,
    outline = Color(0xFF3A3F4B),
    error = Color(0xFFFF8A80),
)

private fun style(size: Int, weight: FontWeight, line: Int = (size * 1.3).toInt()) =
    TextStyle(fontFamily = Tajawal, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

private val typography = Typography(
    displaySmall = style(36, FontWeight.ExtraBold, 42),
    headlineLarge = style(32, FontWeight.ExtraBold),
    headlineMedium = style(28, FontWeight.Bold),
    headlineSmall = style(24, FontWeight.Bold),
    titleLarge = style(21, FontWeight.Bold),
    titleMedium = style(17, FontWeight.Bold),
    titleSmall = style(15, FontWeight.Medium),
    bodyLarge = style(16, FontWeight.Normal),
    bodyMedium = style(14, FontWeight.Normal),
    bodySmall = style(12, FontWeight.Normal),
    labelLarge = style(15, FontWeight.Bold),
    labelMedium = style(13, FontWeight.Medium),
    labelSmall = style(11, FontWeight.Medium),
)

@Composable
fun ArabTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography) {
        // Text outside a Card/Surface would otherwise fall back to black on the dark background.
        CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
    }
}

/** A visible focus ring for D-pad navigation on TV. */
@Composable
fun focusBorder(source: MutableInteractionSource): BorderStroke? {
    val focused by source.collectIsFocusedAsState()
    return if (focused) BorderStroke(3.dp, Color.White) else null
}

/** Apple TV-style lift: the focused item grows a little. */
@Composable
fun Modifier.focusScale(source: MutableInteractionSource, scale: Float = 1.08f): Modifier {
    val focused by source.collectIsFocusedAsState()
    val s by animateFloatAsState(if (focused) scale else 1f, tween(160), label = "focusScale")
    return this.graphicsLayer {
        scaleX = s
        scaleY = s
    }
}
