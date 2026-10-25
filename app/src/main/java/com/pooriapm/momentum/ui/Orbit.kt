package com.pooriapm.momentum.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// Orbit: the day's rings. Each ring is one plan item for one period (today), counted from data the
// app really has. A ring without a known count or target draws a dashed track and says why; it is
// never shown as zero. Done beyond the plan does not lap the ring.

object OrbitColors {
    val Hero = Color(0xFF252E27)
    val HeroText = Color(0xFFF7F4E9)
    val HeroMuted = Color(0xFFC9CCBE)
    val Track = Color(0xFF485248)
    val Food = Color(0xFFE7AD49)
    val Training = Color(0xFF74B58D)
    val Water = Color(0xFF8CC3E0)
}

sealed interface RingState {
    /** [done] of [planned] [unit]; planned > 0. */
    data class Progress(val done: Double, val planned: Double, val text: String) : RingState
    /** Nothing planned for the period on purpose (a rest day). */
    data class Rest(val text: String) : RingState
    /** No count or no target yet: not zero, not failure. */
    data class Unknown(val text: String) : RingState
}

data class OrbitRing(val label: String, val color: Color, val state: RingState)

@Composable
fun OrbitHero(title: String, subtitle: String, period: String, rings: List<OrbitRing>, footer: String?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(OrbitColors.Hero).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = OrbitColors.HeroText)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = OrbitColors.HeroMuted)
            }
            Spacer(Modifier.width(12.dp))
            OrbitRings(rings, Modifier.size(128.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(period, style = MaterialTheme.typography.labelMedium, color = OrbitColors.HeroMuted)
            rings.forEach { OrbitLegendRow(it) }
        }
        footer?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = OrbitColors.HeroMuted) }
    }
}

@Composable
private fun OrbitLegendRow(ring: OrbitRing) {
    val value = when (val s = ring.state) {
        is RingState.Progress -> s.text
        is RingState.Rest -> s.text
        is RingState.Unknown -> s.text
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 40.dp).semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(ring.color))
        Spacer(Modifier.width(10.dp))
        Text(ring.label, style = MaterialTheme.typography.titleSmall, color = OrbitColors.HeroText, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OrbitColors.HeroText)
    }
}

/** Up to three concentric rings, outermost first. Decorative for TalkBack: the legend says the same. */
@Composable
fun OrbitRings(rings: List<OrbitRing>, modifier: Modifier = Modifier) {
    val reduceMotion = LocalPerf.current.reduceMotion
    val fractions = rings.map { ring ->
        val target = (ring.state as? RingState.Progress)?.let { (it.done / it.planned).toFloat().coerceIn(0f, 1f) } ?: 0f
        val animated by animateFloatAsState(target, if (reduceMotion) snap() else tween(240), label = "orbit-${ring.label}")
        animated
    }
    Canvas(modifier.clearAndSetSemantics {}) {
        val stroke = 9.dp.toPx()
        val gap = 6.dp.toPx()
        rings.forEachIndexed { i, ring ->
            val inset = stroke / 2 + i * (stroke + gap)
            val topLeft = Offset(inset, inset)
            val arcSize = Size(size.width - 2 * inset, size.height - 2 * inset)
            if (arcSize.width <= 0f) return@forEachIndexed
            val dashed = ring.state !is RingState.Progress
            drawArc(
                color = if (ring.state is RingState.Rest) OrbitColors.Track.copy(alpha = 0.55f) else OrbitColors.Track,
                startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = topLeft, size = arcSize,
                style = Stroke(
                    width = stroke,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx())) else null,
                ),
            )
            // A real zero has no arc at all (a round cap would draw a misleading dot).
            val fraction = fractions[i]
            if (fraction > 0.001f) {
                drawArc(
                    color = ring.color,
                    startAngle = -90f, sweepAngle = 360f * fraction, useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(width = stroke, cap = if (fraction >= 0.999f) StrokeCap.Butt else StrokeCap.Round),
                )
            }
        }
    }
}
