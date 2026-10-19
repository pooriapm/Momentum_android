package com.pooriapm.momentum.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.abs

private val Weekdays = listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")

fun weekdayName(iso: String): String =
    runCatching { Weekdays[LocalDate.parse(iso).dayOfWeek.value - 1] }.getOrDefault("")

/**
 * The plan's days without a list: a ring that fills as the month goes by (today marked on it),
 * "day N of 30" in the middle, and arrows to step a day back or forward. Swiping sideways also
 * steps (right-to-left aware), and "today" jumps back when you have wandered off.
 */
@Composable
fun DayNavigator(
    index: Int,
    count: Int,
    todayIndex: Int,
    date: String,
    training: Boolean,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    fun go(to: Int) {
        val target = to.coerceIn(0, count - 1)
        if (target != index) {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            onChange(target)
        }
    }
    var drag by remember { mutableFloatStateOf(0f) }

    Column(
        modifier
            .fillMaxWidth()
            .pointerInput(index, count) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        // The next day sits on the end side (left in Persian): dragging toward the start reveals it.
                        if (abs(drag) > 60f) go(index + if ((drag < 0) == rtl) -1 else 1)
                        drag = 0f
                    },
                    onDragCancel = { drag = 0f },
                ) { _, amount -> drag += amount }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            // Start side (right in Persian) = previous day, arrow pointing out; end side = next day.
            StepButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "روز قبل", enabled = index > 0) { go(index - 1) }
            ProgressRing(index, count, todayIndex)
            StepButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "روز بعد", enabled = index < count - 1) { go(index + 1) }
        }
        AnimatedContent(
            targetState = index,
            transitionSpec = {
                val forward = targetState > initialState
                val sign = if (forward == rtl) -1 else 1
                (slideInHorizontally(tween(260)) { it / 3 * sign } + fadeIn(tween(260)))
                    .togetherWith(slideOutHorizontally(tween(200)) { -it / 3 * sign } + fadeOut(tween(160)))
            },
            label = "day-label",
        ) { _ ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${weekdayName(date)} ${jalaliLabel(date)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        index == todayIndex -> if (training) "امروز · روز تمرین" else "امروز · روز استراحت"
                        training -> "روز تمرین"
                        else -> "روز استراحت"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (index == todayIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(
            index != todayIndex && todayIndex >= 0,
            // Grow and shrink in height, so the cards below glide instead of jumping.
            enter = expandVertically(spring(dampingRatio = 0.85f, stiffness = 380f), expandFrom = Alignment.Top) +
                fadeIn(tween(220, delayMillis = 60)) + scaleIn(tween(220, delayMillis = 60), initialScale = 0.9f),
            exit = shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 420f), shrinkTowards = Alignment.Top) +
                fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.9f),
        ) {
            Text(
                "بازگشت به امروز",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .clickable(role = Role.Button) { go(todayIndex) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun StepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colors.surface.copy(alpha = if (enabled) 0.9f else 0.4f))
            .border(1.dp, colors.outlineVariant, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (enabled) colors.onSurface else colors.outline,
        )
    }
}

/** The month as a ring: filled up to the shown day, a dot where today is. */
@Composable
private fun ProgressRing(index: Int, count: Int, todayIndex: Int) {
    val colors = MaterialTheme.colorScheme
    val fraction by animateFloatAsState((index + 1).toFloat() / count, spring(dampingRatio = 0.8f, stiffness = 300f), label = "ring")
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(Modifier.size(116.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(116.dp)) {
            val stroke = 9.dp.toPx()
            val inset = stroke / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            // Persian reads right to left, so the ring fills counter-clockwise from the top.
            val direction = if (rtl) -1f else 1f
            drawArc(colors.surfaceVariant, -90f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            drawArc(colors.primary, -90f, 360f * fraction * direction, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (todayIndex in 0 until count) {
                // Where the filled arc ends on today, so on today's page the dot sits exactly on its tip.
                val angle = Math.toRadians((-90.0 + 360.0 * (todayIndex + 1) / count * direction))
                val r = arcSize.width / 2
                val c = Offset(size.width / 2 + r * kotlin.math.cos(angle).toFloat(), size.height / 2 + r * kotlin.math.sin(angle).toFloat())
                drawCircle(colors.surface, stroke * 0.55f, c)
                drawCircle(colors.secondary, stroke * 0.38f, c)
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("روز", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            AnimatedContent(index, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.8f)).togetherWith(fadeOut()) }, label = "ring-number") { i ->
                Text((i + 1).fa(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            Text("از ${count.fa()}", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}
