package com.pooriapm.momentum.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pooriapm.momentum.ui.theme.Glow
import com.pooriapm.momentum.ui.theme.LocalGlow
import com.pooriapm.momentum.ui.theme.hueBackground
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Ink = Color(0xFF1C1A17)
private val Ember = Color(0xFFE0592A)
private val Cream = Color(0xFFF4EFE6)

/** Floating glass bubbles: x (0–1), size (dp), speed factor, phase. */
private val Bubbles = listOf(
    floatArrayOf(0.12f, 34f, 1.0f, 0.10f), floatArrayOf(0.82f, 22f, 1.4f, 0.55f),
    floatArrayOf(0.30f, 14f, 1.8f, 0.80f), floatArrayOf(0.68f, 46f, 0.8f, 0.30f),
    floatArrayOf(0.90f, 16f, 1.6f, 0.05f), floatArrayOf(0.05f, 20f, 1.3f, 0.65f),
    floatArrayOf(0.50f, 12f, 2.0f, 0.42f), floatArrayOf(0.40f, 28f, 1.1f, 0.92f),
)

/**
 * Animated brand intro: drifting hue, rising glass bubbles, pulse rings, and the logo assembling
 * itself (ink disc springs in, the ember ring draws, its dot lands, the M rises), then the wordmark
 * and tagline. While data is still loading the ring keeps orbiting, so it doubles as the spinner.
 */
@Composable
fun BrandSplash(modifier: Modifier = Modifier) {
    val glow = LocalGlow.current
    val disc = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    val dot = remember { Animatable(0f) }
    val mark = remember { Animatable(0f) }
    val word = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { disc.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)) }
        delay(150)
        launch { ring.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
        delay(350)
        launch { mark.animateTo(1f, tween(500, easing = FastOutSlowInEasing)) }
        delay(550)
        launch { dot.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium)) }
        launch { word.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        delay(250)
        tagline.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }

    val ambient = rememberInfiniteTransition(label = "ambient")
    val pulse by ambient.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "pulse")
    val orbit by ambient.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "orbit")

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Lite: a still background. The moving one redraws the whole screen every frame.
        if (LocalPerf.current.lite) Box(Modifier.fillMaxSize().hueBackground(glow)) else AmbientBackground(glow, ambient, tagline.value, disc.value)

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Canvas(Modifier.size(220.dp)) { drawLogo(disc.value, ring.value, dot.value, mark.value, pulse, if (tagline.value >= 1f) orbit else 0f) }
            Spacer(Modifier.height(4.dp))
            Text(
                "Momentum",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                letterSpacing = (10f - 9f * word.value).sp,
                modifier = Modifier.graphicsLayer { alpha = word.value; translationY = (1 - word.value) * 24f },
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "یک ماه غذا و تمرین، چیده‌شده برای تو",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { alpha = tagline.value; translationY = (1 - tagline.value) * 18f },
            )
        }

        // A quiet loading cue at the bottom: three dots breathing in turn.
        Canvas(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp).size(width = 56.dp, height = 10.dp)) {
            repeat(3) { i ->
                val phase = ((pulse * 3f - i) % 3f + 3f) % 3f
                val a = (0.25f + 0.75f * (if (phase < 1f) sin(phase * PI.toFloat()) else 0f)) * tagline.value
                drawCircle(Ember.copy(alpha = a), 4.dp.toPx(), Offset(size.width / 2 + (i - 1) * 18.dp.toPx(), size.height / 2))
            }
        }
    }
}

/** Drifting hue blobs and rising glass bubbles behind the intro. */
@Composable
private fun AmbientBackground(glow: Glow, ambient: InfiniteTransition, tagline: Float, disc: Float) {
    val drift by ambient.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val rise by ambient.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing)), label = "rise")
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(glow.base)
        fun blob(color: Color, x: Float, y: Float, r: Float) {
            val c = Offset(w * x, h * y)
            drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), c, w * r), w * r, c)
        }
        blob(glow.accent, 0.85f - 0.15f * drift, 0.08f + 0.06f * drift, 1.0f)
        blob(glow.rose, 0.05f + 0.12f * drift, 0.45f - 0.05f * drift, 0.8f)
        blob(glow.amber, 0.95f - 0.1f * drift, 0.72f + 0.05f * drift, 0.7f)
        blob(glow.highlight, 0.2f + 0.1f * drift, 0.98f - 0.04f * drift, 0.9f)
        Bubbles.forEach { (x, sizeDp, speed, phase) ->
            val t = ((rise * speed + phase) % 1f)
            val r = sizeDp.dp.toPx() / 2
            val center = Offset(w * x + sin((t + phase) * 2 * PI.toFloat()) * 18.dp.toPx(), h * (1.1f - t * 1.2f))
            val fade = sin(t * PI.toFloat()) * tagline.coerceAtLeast(disc * 0.6f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.55f * fade), Ember.copy(alpha = 0.10f * fade), Color.Transparent),
                    center = center - Offset(r * 0.3f, r * 0.3f), radius = r * 1.3f,
                ),
                r, center,
            )
            drawCircle(Color.White.copy(alpha = 0.35f * fade), r, center, style = Stroke(1.dp.toPx()))
        }
    }
}

/**
 * The logo at rest, alive: the ember ring slowly orbits the M (one turn every 14 s) and its dot
 * breathes, forever. Quiet enough to sit above a form.
 */
@Composable
fun LiveLogo(modifier: Modifier = Modifier) {
    if (LocalPerf.current.reduceMotion) {
        Canvas(modifier) { drawLogo(disc = 1f, ring = 1f, dot = 1f, mark = 1f, pulse = 0.5f, orbit = 0f, rings = false) }
        return
    }
    val motion = rememberInfiniteTransition(label = "live-logo")
    val orbit by motion.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "orbit")
    val breath by motion.animateFloat(0.82f, 1.12f, infiniteRepeatable(tween(1_600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "dot")
    Canvas(modifier) { drawLogo(disc = 1f, ring = 1f, dot = breath, mark = 1f, pulse = 0.5f, orbit = orbit, rings = false) }
}

/** The app icon, drawn in steps so each part can animate: disc, ring, dot, M, and pulse rings. */
private fun DrawScope.drawLogo(disc: Float, ring: Float, dot: Float, mark: Float, pulse: Float, orbit: Float, rings: Boolean = true) {
    val c = center
    val discR = size.minDimension * 0.30f
    // Pulse rings radiating out once the disc is in.
    if (rings) repeat(2) { i ->
        val p = (pulse + i * 0.5f) % 1f
        drawCircle(
            Ember.copy(alpha = (1f - p) * 0.35f * disc.coerceIn(0f, 1f)),
            radius = discR * (1f + p * 0.75f),
            center = c,
            style = Stroke(2.dp.toPx() * (1f - p) + 0.5f),
        )
    }
    // Soft shadow + ink disc.
    scale(disc, pivot = c) {
        drawCircle(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.18f), Color.Transparent), c + Offset(0f, 8.dp.toPx()), discR * 1.25f), discR * 1.25f, c + Offset(0f, 8.dp.toPx()))
        drawCircle(Ink, discR, c)
        // A faint top highlight gives the disc a glassy finish.
        drawCircle(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), c.y - discR, c.y), discR, c)
    }
    // Ember ring: an open circle drawn from the dot's end, clockwise, leaving a gap at the top.
    val ringR = discR * 0.70f
    val stroke = discR * 0.12f
    val start = -60f
    val sweep = 290f * ring
    rotate(orbit, c) {
        if (sweep > 0f) {
            drawArc(
                Ember, startAngle = start, sweepAngle = sweep, useCenter = false,
                topLeft = Offset(c.x - ringR, c.y - ringR), size = Size(ringR * 2, ringR * 2),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        // The dot sits on the ring's starting end and lands with a bounce.
        val a = Math.toRadians(start.toDouble())
        val dotCenter = Offset(c.x + ringR * cos(a).toFloat(), c.y + ringR * sin(a).toFloat())
        drawCircle(Ember, stroke * 0.95f * dot, dotCenter)
    }
    // The M rises and fades in.
    if (mark > 0f) {
        val m = discR * 0.62f
        translate(c.x - m / 2, c.y - m / 2 + (1f - mark) * m * 0.25f) {
            drawPath(mPath(m), Cream.copy(alpha = mark))
        }
    }
}

/** A bold geometric M in a [s]×[s] box. */
private fun mPath(s: Float) = Path().apply {
    val u = s / 100f
    moveTo(10 * u, 92 * u)
    lineTo(10 * u, 10 * u)
    lineTo(30 * u, 10 * u)
    lineTo(50 * u, 46 * u)
    lineTo(70 * u, 10 * u)
    lineTo(90 * u, 10 * u)
    lineTo(90 * u, 92 * u)
    lineTo(71 * u, 92 * u)
    lineTo(71 * u, 44 * u)
    lineTo(55 * u, 72 * u)
    lineTo(45 * u, 72 * u)
    lineTo(29 * u, 44 * u)
    lineTo(29 * u, 92 * u)
    close()
}
