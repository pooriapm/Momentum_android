package com.pooriapm.momentum.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private enum class DonePhase { Idle, Busy, Tick, Undo }

/**
 * "Mark as done" as a small animation instead of a word: a round tick button; while saving it
 * spins; on success it fills green and the tick draws itself, then it turns into an
 * "undo" pill. Undoing spins again and shrinks back to the round button.
 *
 * [done] and [busy] come from the server state, so the control always shows the truth; the
 * celebration only plays when it changes to done while on screen.
 */
@Composable
fun DoneToggle(
    done: Boolean,
    busy: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    actionLabel: String = "خوردم",
    undoLabel: String = "حذف ثبت",
) {
    val haptics = LocalHapticFeedback.current
    var phase by remember { mutableStateOf(if (done) DonePhase.Undo else DonePhase.Idle) }
    LaunchedEffect(done, busy) {
        phase = when {
            busy -> DonePhase.Busy
            done && phase == DonePhase.Busy -> {
                haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                phase = DonePhase.Tick
                delay(900)
                DonePhase.Undo
            }
            done -> DonePhase.Undo
            else -> DonePhase.Idle
        }
    }

    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            (fadeIn(tween(220, delayMillis = 60)) + scaleIn(spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.7f))
                .togetherWith(fadeOut(tween(140)) + scaleOut(tween(140), targetScale = 0.85f))
                .using(SizeTransform(clip = false))
        },
        contentAlignment = Alignment.CenterStart,
        label = "done",
        modifier = modifier,
    ) { current ->
        when (current) {
            DonePhase.Idle -> RoundButton(enabled, actionLabel, onToggle)
            DonePhase.Busy -> Spinner()
            DonePhase.Tick -> DrawnTick()
            DonePhase.Undo -> UndoPill(enabled, undoLabel, onToggle)
        }
    }
}

@Composable
private fun RoundButton(enabled: Boolean, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .border(1.5.dp, if (enabled) colors.primary else colors.outlineVariant, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Check, contentDescription = null, tint = if (enabled) colors.primary else colors.outline)
    }
}

@Composable
private fun Spinner() {
    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(32.dp), color = MaterialTheme.colorScheme.tertiary)
    }
}

/** Green disc that pops in while the tick is drawn stroke by stroke. */
@Composable
private fun DrawnTick() {
    val green = MaterialTheme.colorScheme.tertiary
    val onGreen = MaterialTheme.colorScheme.onTertiary
    val pop = remember { Animatable(0.6f) }
    val draw = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium))
    }
    LaunchedEffect(Unit) {
        delay(80)
        draw.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
    }
    Canvas(Modifier.size(48.dp).scale(pop.value)) {
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
        drawPath(partial, onGreen, style = Stroke(width = s * 0.09f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
private fun UndoPill(enabled: Boolean, label: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(colors.tertiaryContainer)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 6.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(colors.tertiary), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onTertiary, modifier = Modifier.size(18.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = colors.tertiary)
    }
}
