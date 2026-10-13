package com.pooriapm.momentum.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

// Same tokens as the website's dark theme (Momentum/src/styles/theme.css).
val Background = Color(0xFF0E0D0B)
val Surface = Color(0xFF1A1916)
val SurfaceRaised = Color(0xFF24221F)
val TextPrimary = Color(0xFFF4F1EA)
val TextSecondary = Color(0xFFB3AC9F)
val Brand = Color(0xFFF0A93B)
val BrandSoft = Color(0xFF33270F)
val Energy = Color(0xFFFF8A5C)
val Success = Color(0xFF4CC38A)

private val colors = darkColorScheme(
    primary = Brand,
    onPrimary = Background,
    primaryContainer = BrandSoft,
    onPrimaryContainer = TextPrimary,
    secondary = Energy,
    tertiary = Success,
    background = Background,
    onBackground = TextPrimary,
    surface = Surface,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = Surface,
    surfaceContainerHigh = SurfaceRaised,
)

/** Persian-only app: always right-to-left, whatever the device language. */
@Composable
fun MomentumTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
