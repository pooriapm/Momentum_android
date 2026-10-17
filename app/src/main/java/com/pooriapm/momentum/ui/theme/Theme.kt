package com.pooriapm.momentum.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

// "Saffron & Ink", the website's palette (Momentum/src/styles/theme.css). The dark mode is
// lifted from near-black to a warm charcoal so long sessions are easier on the eyes.

private val Light = lightColorScheme(
    primary = Color(0xFFA35E00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFCF2E1),
    onPrimaryContainer = Color(0xFF3B2200),
    secondary = Color(0xFFB5441A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFDEEE7),
    onSecondaryContainer = Color(0xFF3D1406),
    tertiary = Color(0xFF2B7A4B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3F2E8),
    background = Color(0xFFF6F4F0),
    onBackground = Color(0xFF1C1A17),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1A17),
    surfaceVariant = Color(0xFFEFECE6),
    onSurfaceVariant = Color(0xFF5E5951),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF9F6),
    surfaceContainer = Color(0xFFF1EEE9),
    surfaceContainerHigh = Color(0xFFEFECE6),
    surfaceContainerHighest = Color(0xFFE9E5DE),
    outline = Color(0xFF8A847A),
    outlineVariant = Color(0xFFE3DFD7),
    error = Color(0xFFC02942),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFCE8EB),
    onErrorContainer = Color(0xFF5C0A18),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFF0A93B),
    onPrimary = Color(0xFF2A1A00),
    primaryContainer = Color(0xFF3E3018),
    onPrimaryContainer = Color(0xFFFCE6C2),
    secondary = Color(0xFFFF8A5C),
    onSecondary = Color(0xFF3A1406),
    secondaryContainer = Color(0xFF45261A),
    onSecondaryContainer = Color(0xFFFFDCCD),
    tertiary = Color(0xFF4CC38A),
    onTertiary = Color(0xFF00391E),
    tertiaryContainer = Color(0xFF1C3A2A),
    // Warm charcoal instead of #0E0D0B: clearly dark, not a black void.
    background = Color(0xFF1D1B18),
    onBackground = Color(0xFFF4F1EA),
    surface = Color(0xFF26231F),
    onSurface = Color(0xFFF4F1EA),
    surfaceVariant = Color(0xFF332F2A),
    onSurfaceVariant = Color(0xFFC2BBAE),
    surfaceContainerLowest = Color(0xFF191714),
    surfaceContainerLow = Color(0xFF221F1B),
    surfaceContainer = Color(0xFF292622),
    surfaceContainerHigh = Color(0xFF312E29),
    surfaceContainerHighest = Color(0xFF3A3631),
    outline = Color(0xFF8A847A),
    outlineVariant = Color(0xFF45413B),
    error = Color(0xFFFF6F80),
    onError = Color(0xFF4A0A15),
    errorContainer = Color(0xFF4A1D24),
    onErrorContainer = Color(0xFFFFD9DE),
)

/** Colors of the soft background glows ("hue") behind the app. */
@Immutable
data class Glow(val base: Color, val accent: Color, val highlight: Color)

private val LightGlow = Glow(Color(0xFFF6F4F0), Color(0x2EE0901F), Color(0x1FD9592A))
private val DarkGlow = Glow(Color(0xFF1D1B18), Color(0x26F0A93B), Color(0x1AFF8A5C))

val LocalGlow = staticCompositionLocalOf { LightGlow }

// Rounder corners than the M3 default, matching the website's soft cards.
val MomentumShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

/**
 * Persian-only app: always right-to-left. Light or dark follows the device setting, like the
 * website's "system appearance".
 */
@Composable
fun MomentumTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme: ColorScheme = if (dark) Dark else Light
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalGlow provides if (dark) DarkGlow else LightGlow,
    ) {
        MaterialTheme(colorScheme = scheme, typography = MomentumTypography, shapes = MomentumShapes, content = content)
    }
}

/**
 * The app's backdrop: the base color with two large, very soft radial glows — saffron from the
 * top corner, ember from the bottom — so screens have depth without an image or visual noise.
 */
@Composable
fun GlowBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val glow = LocalGlow.current
    Box(
        modifier
            .fillMaxSize()
            .background(glow.base)
            .drawBehind {
                val w = size.width
                val h = size.height
                drawCircle(
                    brush = Brush.radialGradient(listOf(glow.accent, Color.Transparent), center = Offset(w * 0.85f, h * 0.05f), radius = w * 1.1f),
                    radius = w * 1.1f,
                    center = Offset(w * 0.85f, h * 0.05f),
                )
                drawCircle(
                    brush = Brush.radialGradient(listOf(glow.highlight, Color.Transparent), center = Offset(w * 0.1f, h * 0.95f), radius = w * 0.9f),
                    radius = w * 0.9f,
                    center = Offset(w * 0.1f, h * 0.95f),
                )
            },
    ) {
        // Text without an explicit color reads the content color; without a Surface it would be black.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) { content() }
    }
}
