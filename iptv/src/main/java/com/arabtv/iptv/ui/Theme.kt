package com.arabtv.iptv.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

val Brand = Color(0xFF0E7C66)
val Gold = Color(0xFFE3B341)
val Bg = Color(0xFF0B0F14)
val Surface1 = Color(0xFF151B23)
val Surface2 = Color(0xFF1E2630)

private val scheme = darkColorScheme(
    primary = Color(0xFF3FBF9F),
    onPrimary = Color(0xFF00201A),
    primaryContainer = Brand,
    onPrimaryContainer = Color.White,
    secondary = Gold,
    onSecondary = Color(0xFF261A00),
    background = Bg,
    onBackground = Color(0xFFE6EDF3),
    surface = Bg,
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Surface2,
    onSurfaceVariant = Color(0xFFA9B4C0),
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    surfaceContainerHighest = Color(0xFF27313D),
    surfaceContainerLow = Surface1,
    error = Color(0xFFFF8A80),
)

@Composable
fun ArabTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

/** A visible focus ring for D-pad navigation on TV. */
@Composable
fun focusBorder(source: MutableInteractionSource): BorderStroke? {
    val focused by source.collectIsFocusedAsState()
    return if (focused) BorderStroke(3.dp, Gold) else null
}
