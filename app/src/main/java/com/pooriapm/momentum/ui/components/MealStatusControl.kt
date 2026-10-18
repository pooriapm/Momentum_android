package com.pooriapm.momentum.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private enum class MealPhase { Planned, Busy, Tick, Eaten, Skipped }

/** Fixed footprint: every phase fits here, so the card never changes height while animating. */
private val ControlWidth = 116.dp
private val ControlHeight = 34.dp

/**
 * A meal's status, compact enough to sit under its time. Planned: a tick ("I ate it") and a skip
 * button. While saving: a spinner. Eaten: the disc fills green and the tick draws itself, then it
 * becomes an "undo" pill. Skipped: a neutral "undo" pill. [status] is the server's value
 * (planned / completed / skipped), so the control always shows what is saved; the celebration
 * plays only when a meal becomes eaten while on screen.
 */
@Composable
fun MealStatusControl(
    status: String,
    busy: Boolean,
    enabled: Boolean,
    onEat: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    fun settled() = when (status) {
        "completed" -> MealPhase.Eaten
        "skipped" -> MealPhase.Skipped
        else -> MealPhase.Planned
    }
    var phase by remember { mutableStateOf(settled()) }
    LaunchedEffect(status, busy) {
        if (busy) {
            phase = MealPhase.Busy
            return@LaunchedEffect
        }
        if (status == "completed" && phase == MealPhase.Busy) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            phase = MealPhase.Tick
            delay(850)
        }
        phase = settled()
    }

    Box(modifier.size(ControlWidth, ControlHeight), contentAlignment = Alignment.CenterEnd) {
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (fadeIn(tween(200, delayMillis = 60)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.75f))
                    .togetherWith(fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.85f))
            },
            contentAlignment = Alignment.CenterEnd,
            label = "meal-status",
        ) { current ->
            when (current) {
                MealPhase.Planned -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoundIcon(Icons.Rounded.Check, "خوردم", MaterialTheme.colorScheme.primary, enabled, onEat)
                    RoundIcon(Icons.Rounded.DoNotDisturbOn, "رد کردن وعده", MaterialTheme.colorScheme.onSurfaceVariant, enabled, onSkip)
                }
                MealPhase.Busy -> Box(Modifier.size(ControlHeight), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.tertiary)
                }
                MealPhase.Tick -> DrawnTick()
                MealPhase.Eaten -> UndoPill(
                    icon = Icons.Rounded.Check,
                    iconBackground = MaterialTheme.colorScheme.tertiary,
                    iconTint = MaterialTheme.colorScheme.onTertiary,
                    background = MaterialTheme.colorScheme.tertiaryContainer,
                    textColor = MaterialTheme.colorScheme.tertiary,
                    enabled = enabled,
                    description = "خورده شد؛ حذف ثبت",
                    onClick = onUndo,
                )
                MealPhase.Skipped -> UndoPill(
                    icon = Icons.Rounded.DoNotDisturbOn,
                    iconBackground = MaterialTheme.colorScheme.outline,
                    iconTint = MaterialTheme.colorScheme.surface,
                    background = MaterialTheme.colorScheme.surfaceVariant,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    enabled = enabled,
                    description = "رد شد؛ حذف ثبت",
                    onClick = onUndo,
                )
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, tint: Color, enabled: Boolean, onClick: () -> Unit) {
    val color = if (enabled) tint else MaterialTheme.colorScheme.outlineVariant
    Box(
        Modifier
            .size(ControlHeight)
            .clip(CircleShape)
            .border(1.5.dp, color, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
    }
}

/** Green disc that pops in while the tick draws itself. */
@Composable
private fun DrawnTick() {
    val green = MaterialTheme.colorScheme.tertiary
    val onGreen = MaterialTheme.colorScheme.onTertiary
    val pop = remember { Animatable(0.6f) }
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium)) }
    LaunchedEffect(Unit) {
        delay(60)
        draw.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
    }
    Canvas(Modifier.size(ControlHeight).scale(pop.value)) {
        drawCircle(green)
        val s = size.minDimension
        val tick = Path().apply {
            moveTo(s * 0.28f, s * 0.52f)
            lineTo(s * 0.44f, s * 0.67f)
            lineTo(s * 0.73f, s * 0.36f)
        }
        val measure = PathMeasure().apply { setPath(tick, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * draw.value, partial, true)
        drawPath(partial, onGreen, style = Stroke(width = s * 0.1f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun UndoPill(
    icon: ImageVector,
    iconBackground: Color,
    iconTint: Color,
    background: Color,
    textColor: Color,
    enabled: Boolean,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .height(30.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(start = 4.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(iconBackground), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(14.dp))
        }
        Text("حذف ثبت", style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}
