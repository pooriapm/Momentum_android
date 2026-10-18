package com.pooriapm.momentum.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp

private val FieldShape = RoundedCornerShape(16.dp)

/**
 * The app's one text input, drawn entirely in Compose so it looks the same on every Android
 * version and keyboard: label above, a soft rounded surface, a border that warms to saffron on
 * focus, the unit inside the field, and an error line in place of the hint.
 *
 * [ltr] is for values that read left to right inside Persian UI (email, password).
 */
@Composable
fun MomentumField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    suffix: String? = null,
    supporting: String? = null,
    error: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    readOnly: Boolean = false,
    ltr: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val border by animateColorAsState(
        when {
            error != null -> colors.error
            focused -> colors.primary
            else -> colors.outlineVariant
        },
        tween(180), label = "border",
    )
    val borderWidth by animateDpAsState(if (focused || error != null) 1.5.dp else 1.dp, tween(180), label = "width")
    val container by animateColorAsState(
        when {
            focused -> colors.primary.copy(alpha = if (dark) 0.08f else 0.04f).compositeOver(fieldBase(dark))
            else -> fieldBase(dark)
        },
        tween(180), label = "container",
    )
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val textStyle = MaterialTheme.typography.bodyLarge.copy(
        color = colors.onSurface,
        textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content,
        // Pinned to the layout's start edge, not to each paragraph's own direction (Start would
        // follow a Latin word's LTR direction), so typed text always lines up with the label.
        textAlign = if (ltr || !rtl) TextAlign.Left else TextAlign.Right,
    )
    // The hint sits where typed text will start, so it lines up with the input in LTR fields too.
    val hintStyle = textStyle.copy(
        color = colors.onSurfaceVariant.copy(alpha = 0.7f),
    )

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (focused) colors.primary else colors.onSurfaceVariant,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            singleLine = singleLine,
            minLines = minLines,
            textStyle = textStyle,
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            interactionSource = interaction,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { inner ->
                // Email, password, codes: the whole field runs left to right (text on the left,
                // icons on the right), as in every app, even inside the Persian screen.
                CompositionLocalProvider(LocalLayoutDirection provides if (ltr) LayoutDirection.Ltr else LocalLayoutDirection.current) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(if (singleLine) Modifier.height(56.dp) else Modifier.heightIn(min = 56.dp))
                        .clip(FieldShape)
                        .background(container)
                        .border(borderWidth, border, FieldShape)
                        // Single-line fields have one fixed height (a trailing icon button must not
                        // make one field taller than its neighbour); multi-line ones grow.
                        .padding(start = 16.dp, end = if (trailing != null) 4.dp else 16.dp, top = if (singleLine) 0.dp else 14.dp, bottom = if (singleLine) 0.dp else 14.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    // The text box spans the whole field, so text starts at the start edge (right in
                    // Persian) whatever its own direction; short Latin text no longer floats mid-field.
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, style = hintStyle, modifier = Modifier.fillMaxWidth())
                        }
                        Box(Modifier.fillMaxWidth()) { inner() }
                    }
                    suffix?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                    trailing?.invoke()
                }
                }
            },
        )
        val note = error ?: supporting
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) colors.error else colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

/** White on the warm light background, a lifted charcoal in dark mode. */
@Composable
private fun fieldBase(dark: Boolean) =
    if (dark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
