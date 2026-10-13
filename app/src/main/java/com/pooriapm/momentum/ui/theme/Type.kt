package com.pooriapm.momentum.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pooriapm.momentum.R

// Vazirmatn is the website's Persian typeface (OFL, see licenses/).
val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private val base = Typography()

private fun TextStyle.persian(lineHeightScale: Float = 1.5f) =
    copy(fontFamily = Vazirmatn, lineHeight = fontSize * lineHeightScale, letterSpacing = 0.sp)

// Material 3 type scale with Persian-friendly line height (Arabic script needs more room).
val MomentumTypography = Typography(
    displaySmall = base.displaySmall.persian(1.3f).copy(fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.persian(1.35f).copy(fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.persian(1.35f).copy(fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.persian(1.4f).copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.persian(1.4f).copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.persian().copy(fontWeight = FontWeight.Medium),
    titleSmall = base.titleSmall.persian().copy(fontWeight = FontWeight.Medium),
    bodyLarge = base.bodyLarge.persian(1.6f),
    bodyMedium = base.bodyMedium.persian(1.6f),
    bodySmall = base.bodySmall.persian(1.6f),
    labelLarge = base.labelLarge.persian().copy(fontWeight = FontWeight.Medium),
    labelMedium = base.labelMedium.persian(),
    labelSmall = base.labelSmall.persian(),
)
