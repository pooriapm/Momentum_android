package com.pooriapm.momentum.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.data.Checkin
import com.pooriapm.momentum.data.Dashboard
import com.pooriapm.momentum.data.RestaurantTip
import java.time.LocalDate
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

private val Flame = Color(0xFFFF7A1A)
private val FlameCore = Color(0xFFFFC94D)
private val WaterBlue = Color(0xFF3DA5F4)
private val WaterLight = Color(0xFF9AD3FF)

// ---------------------------------------------------------------- Streak

/**
 * Days in a row with something logged: a daily report, or (today) a ticked meal. A day not yet
 * logged today does not break the streak until it is over.
 */
fun streakDays(dashboard: Dashboard): Pair<Int, Boolean> {
    val today = runCatching { LocalDate.parse(dashboard.localDate) }.getOrNull() ?: return 0 to false
    val logged = dashboard.recentCheckins.mapNotNull { runCatching { LocalDate.parse(it.localDate) }.getOrNull() }.toMutableSet()
    val todayDone = dashboard.checkin != null || dashboard.plan?.day?.meals?.any { it.completionStatus == "completed" } == true
    if (todayDone) logged += today
    var day = if (todayDone) today else today.minusDays(1)
    var count = 0
    while (day in logged) {
        count++
        day = day.minusDays(1)
    }
    return count to todayDone
}

@Composable
fun StreakCard(dashboard: Dashboard) {
    val (days, todayDone) = streakDays(dashboard)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedFlame(lit = days > 0, modifier = Modifier.size(44.dp), key = days)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedContent(
                        days,
                        transitionSpec = { (slideInVertically { -it } + fadeIn()).togetherWith(slideOutVertically { it } + fadeOut()) },
                        label = "streak",
                    ) { n ->
                        Text(n.fa(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                            color = if (n > 0) Flame else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(" روز پشت‌سرهم", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 3.dp))
                }
                Text(
                    when {
                        days == 0 -> "امروز یک وعده یا گزارش ثبت کن تا شعله روشن شود."
                        !todayDone -> "امروز هنوز چیزی ثبت نکرده‌ای؛ زنجیره را نگه دار."
                        days >= 7 -> "حرکتت راه افتاده؛ همین را ادامه بده."
                        else -> "هر روز یک قدم؛ زنجیره دارد بلند می‌شود."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** A small flame that flickers and sways; grey and still when the streak is out. */
@Composable
fun AnimatedFlame(lit: Boolean, modifier: Modifier = Modifier, key: Any? = null) {
    // A few flickers, then still: an endless animation would redraw the whole screen (and the
    // glass tab bar behind it) every frame, for nothing.
    val tAnim = remember { Animatable(0f) }
    val still = LocalPerf.current.reduceMotion
    LaunchedEffect(lit, key) {
        if (lit && !still) {
            tAnim.snapTo(0f)
            tAnim.animateTo(3f, tween(4200, easing = LinearEasing))
        }
    }
    val t = tAnim.value % 1f
    val grow by animateFloatAsState(if (lit) 1f else 0.7f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessLow), label = "grow")
    val grey = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier.scale(grow).semantics { contentDescription = if (lit) "شعله روشن" else "شعله خاموش" }) {
        val w = size.width
        val h = size.height
        val phase = t * 2 * PI.toFloat()
        val calm = (1f - (tAnim.value / 3f)).coerceIn(0.25f, 1f)
        val sway = if (lit) sin(phase) * w * 0.05f * calm else 0f
        val stretch = if (lit) 1f + sin(phase * 2) * 0.06f * calm else 1f
        fun flame(scaleW: Float, scaleH: Float): Path = Path().apply {
            val cx = w / 2
            val bottom = h * 0.95f
            val top = bottom - h * 0.85f * scaleH * stretch
            moveTo(cx, bottom)
            cubicTo(cx - w * 0.42f * scaleW, bottom, cx - w * 0.38f * scaleW, bottom - h * 0.45f * scaleH, cx - w * 0.12f * scaleW + sway * 0.5f, bottom - h * 0.6f * scaleH)
            cubicTo(cx - w * 0.05f * scaleW + sway, bottom - h * 0.72f * scaleH, cx + sway, top + h * 0.12f, cx + sway * 1.4f, top)
            cubicTo(cx + w * 0.18f * scaleW + sway, top + h * 0.2f, cx + w * 0.42f * scaleW, bottom - h * 0.4f * scaleH, cx + w * 0.4f * scaleW, bottom - h * 0.22f * scaleH)
            cubicTo(cx + w * 0.38f * scaleW, bottom, cx + w * 0.1f, bottom, cx, bottom)
            close()
        }
        if (lit) {
            drawCircle(Brush.radialGradient(listOf(Flame.copy(alpha = 0.25f), Color.Transparent)), radius = w * 0.55f, center = Offset(w / 2, h * 0.62f))
            drawPath(flame(1f, 1f), Brush.verticalGradient(listOf(Color(0xFFFF4D1A), Flame, FlameCore), startY = 0f, endY = h))
            drawPath(flame(0.5f, 0.55f), Brush.verticalGradient(listOf(FlameCore, Color(0xFFFFF3C4)), startY = h * 0.4f, endY = h))
        } else {
            drawPath(flame(1f, 1f), grey)
        }
    }
}

// ---------------------------------------------------------------- Water

/** Glasses toward today's water target; tap a glass or "+" to add one, it fills with a wave. */
@Composable
fun WaterCard(state: AppState, targetMl: Int) {
    val haptics = LocalHapticFeedback.current
    val glasses = ceil(targetMl / 250.0).toInt().coerceIn(4, 12)
    val drunk = state.waterMl
    val full = drunk / 250
    // Celebrate only the moment the last glass fills here, not a target already met on load.
    val reached = full >= glasses
    var wasReached by remember { mutableStateOf(reached) }
    var party by remember { mutableIntStateOf(0) }
    LaunchedEffect(reached) {
        if (reached && !wasReached) {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            party++
        }
        wasReached = reached
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
      Box {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("آب امروز", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${(drunk / 1000.0).faDecimal()} از ${(targetMl / 1000.0).faDecimal()} لیتر" +
                            if (drunk >= targetMl) " · عالی، رسیدی! 💧" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (drunk >= targetMl) WaterBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                RoundStep(Icons.Rounded.Remove, "یک لیوان کمتر", enabled = drunk > 0) {
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    state.changeWater(-1)
                }
                Spacer(Modifier.width(8.dp))
                RoundStep(Icons.Rounded.Add, "یک لیوان بیشتر", enabled = true, filled = true) {
                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    state.changeWater(+1)
                }
            }
            // One row: the glasses share the width, however many the target needs.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(glasses) { i ->
                    WaterGlass(
                        filled = i < full,
                        modifier = Modifier.weight(1f).aspectRatio(0.72f).clickable(role = Role.Button) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            // Tapping a glass sets the count to it (or one less if it was the last full one).
                            state.changeWater((if (i + 1 == full) i else i + 1) - full)
                        },
                    )
                }
            }
        }
        // Lite: the badge alone; the sparks and confetti are many shapes redrawn every frame.
        if (party > 0 && !LocalPerf.current.reduceMotion) Celebration(party, Modifier.matchParentSize())
        if (party > 0) CongratsBadge(party, Modifier.align(Alignment.Center))
      }
    }
}

/**
 * Fireworks and confetti over the card for ~2 s: three bursts of sparks that fly out, fall with
 * gravity and fade, plus confetti drifting down. Plays once per [key], then draws nothing.
 */
@Composable
private fun Celebration(key: Int, modifier: Modifier) {
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(2200, easing = LinearEasing)) }
    val colors = listOf(WaterBlue, Flame, FlameCore, Color(0xFF4CC38A), Color(0xFFE0708F), WaterLight)
    val bursts = remember(key) {
        val r = kotlin.random.Random(key)
        List(3) { b -> Triple(0.2f + r.nextFloat() * 0.6f, 0.25f + r.nextFloat() * 0.35f, b * 0.18f) }
    }
    val confetti = remember(key) {
        val r = kotlin.random.Random(key * 31)
        List(26) { floatArrayOf(r.nextFloat(), r.nextFloat() * 0.3f, 0.5f + r.nextFloat(), r.nextFloat() * 6.28f, r.nextInt(6).toFloat()) }
    }
    Canvas(modifier) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val w = size.width
        val h = size.height
        bursts.forEachIndexed { b, (bx, by, delay) ->
            val local = ((p - delay) / 0.75f).coerceIn(0f, 1f)
            if (local <= 0f) return@forEachIndexed
            val alpha = (1f - local).coerceIn(0f, 1f)
            val center = Offset(w * bx, h * by)
            repeat(14) { i ->
                val angle = i / 14f * 2 * PI.toFloat() + b
                val dist = w * 0.22f * kotlin.math.sqrt(local)
                val fall = h * 0.35f * local * local
                val pos = center + Offset(kotlin.math.cos(angle) * dist, kotlin.math.sin(angle) * dist + fall)
                val c = colors[(i + b) % colors.size]
                drawCircle(c.copy(alpha = alpha), radius = (3.2f * (1f - local * 0.5f)).dp.toPx(), center = pos)
                drawCircle(c.copy(alpha = alpha * 0.35f), radius = 7.dp.toPx() * (1f - local), center = pos)
            }
        }
        confetti.forEach { (cx, cy, speed, spin, ci) ->
            val y = (cy + p * speed) * h
            if (y > h) return@forEach
            val x = cx * w + sin(spin + p * 9f) * 10.dp.toPx()
            val a = (1f - p).coerceIn(0f, 1f)
            val sz = 6.dp.toPx()
            drawRect(colors[ci.toInt()].copy(alpha = a), topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(sz, sz * (0.4f + 0.6f * kotlin.math.abs(sin(spin + p * 12f)))))
        }
    }
}

/** "You did it" chip that pops in during the celebration and fades away. */
@Composable
private fun CongratsBadge(key: Int, modifier: Modifier) {
    val t = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { t.animateTo(1f, tween(2600, easing = LinearEasing)) }
    val p = t.value
    if (p >= 1f) return
    val pop = if (p < 0.15f) p / 0.15f else 1f
    val alpha = if (p > 0.75f) ((1f - p) / 0.25f) else 1f
    Text(
        "آفرین! آب امروز کامل شد 🎉",
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = alpha),
        modifier = modifier
            .scale(0.6f + 0.4f * pop)
            .clip(RoundedCornerShape(50))
            .background(WaterBlue.copy(alpha = 0.92f * alpha))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun RoundStep(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, filled: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (filled) WaterBlue else Color.Transparent)
            .border(1.5.dp, if (enabled) WaterBlue else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) Color.White else if (enabled) WaterBlue else MaterialTheme.colorScheme.outlineVariant)
    }
}

/** A glass that fills from the bottom with a moving wave on top. */
@Composable
private fun WaterGlass(filled: Boolean, modifier: Modifier) {
    val level by animateFloatAsState(if (filled) 0.86f else 0f, spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow), label = "level")
    // The wave sloshes for a moment after the glass changes, then the water is still.
    val waveAnim = remember { Animatable(0f) }
    val still = LocalPerf.current.reduceMotion
    LaunchedEffect(filled) {
        // Lite: the water settles at once.
        if (still) waveAnim.snapTo(2f)
        else {
            waveAnim.snapTo(0f)
            if (filled) waveAnim.animateTo(2f, tween(2200, easing = LinearEasing))
        }
    }
    val wave = waveAnim.value % 1f
    val slosh = (1f - waveAnim.value / 2f).coerceIn(0.15f, 1f)
    val outline = MaterialTheme.colorScheme.outline
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val glass = Path().apply {
            moveTo(w * 0.08f, h * 0.06f)
            lineTo(w * 0.92f, h * 0.06f)
            lineTo(w * 0.80f, h * 0.96f)
            lineTo(w * 0.20f, h * 0.96f)
            close()
        }
        if (level > 0.01f) {
            clipPath(glass) {
                val surface = h * 0.96f - (h * 0.9f) * level
                val water = Path().apply {
                    moveTo(0f, h)
                    lineTo(0f, surface)
                    var x = 0f
                    while (x <= w) {
                        lineTo(x, surface + sin((x / w + wave) * 2 * PI.toFloat()) * h * 0.035f * slosh)
                        x += w / 12
                    }
                    lineTo(w, h)
                    close()
                }
                drawPath(water, Brush.verticalGradient(listOf(WaterLight, WaterBlue), startY = surface, endY = h))
            }
        }
        drawPath(glass, if (level > 0.01f) WaterBlue else outline.copy(alpha = 0.6f), style = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round))
    }
}

// ---------------------------------------------------------------- Eating out

private val DefaultTips = listOf(
    RestaurantTip("کباب یا مرغ کبابی", listOf("نان یا برنج را نصف بخواه", "سالاد یا سبزی کنارش بگیر", "نوشابه را با دوغ یا آب عوض کن")),
    RestaurantTip("ساندویچ یا فست‌فود", listOf("نان کوچک‌تر یا یک‌لایه بخواه", "سس را جدا بگیر و کم بزن", "سیب‌زمینی سرخ‌کرده را با سالاد عوض کن")),
    RestaurantTip("غذای خانگی یا خورش", listOf("برنج را یک کفگیر کمتر بکش", "خورش با گوشت یا حبوبات را انتخاب کن", "ماست یا سالاد شیرازی کنارش بخور")),
)

/** "I'm eating out": the plan's restaurant suggestions (or simple defaults) in a sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EatingOutSheet(tips: List<RestaurantTip>, onDismiss: () -> Unit) {
    val list = tips.ifEmpty { DefaultTips }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BouncingPlate(Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("امروز بیرون غذا می‌خوری؟", style = MaterialTheme.typography.headlineSmall)
                    Text("چند انتخاب ساده که برنامه‌ات را خراب نمی‌کند.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            list.forEachIndexed { i, tip ->
                val still = LocalPerf.current.reduceMotion
                val appear = remember { Animatable(if (still) 1f else 0f) }
                LaunchedEffect(Unit) { if (!still) appear.animateTo(1f, tween(320, delayMillis = 80 * i, easing = FastOutSlowInEasing)) }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth().scale(0.94f + 0.06f * appear.value),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(tip.title, style = MaterialTheme.typography.titleMedium)
                        tip.orderInstructions.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        tip.estimatedNutrition?.let {
                            Text("حدود ${it.calories.faRounded()} کالری · پروتئین ${it.proteinG.faRounded()}",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            Text("بعد از غذا، وعده را در «امروز» تیک بزن یا ردش کن تا حسابت درست بماند.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A plate with a little fork that bobs, for the eating-out sheet and button. */
@Composable
fun BouncingPlate(modifier: Modifier = Modifier) {
    val bobAnim = remember { Animatable(0f) }
    val still = LocalPerf.current.reduceMotion
    LaunchedEffect(Unit) {
        if (!still) repeat(3) {
            bobAnim.animateTo(1f, tween(450, easing = FastOutSlowInEasing))
            bobAnim.animateTo(0f, tween(450, easing = FastOutSlowInEasing))
        }
    }
    val bob = bobAnim.value
    val primary = MaterialTheme.colorScheme.primary
    val plate = MaterialTheme.colorScheme.surface
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val lift = bob * h * 0.06f
        drawCircle(primary.copy(alpha = 0.15f), w * 0.46f, Offset(w / 2, h * 0.55f))
        drawCircle(plate, w * 0.36f, Offset(w / 2, h * 0.55f - lift))
        drawCircle(primary.copy(alpha = 0.35f), w * 0.36f, Offset(w / 2, h * 0.55f - lift), style = Stroke(2.dp.toPx()))
        drawCircle(primary.copy(alpha = 0.2f), w * 0.22f, Offset(w / 2, h * 0.55f - lift))
        // Fork, tilting with the bob.
        val fx = w * 0.82f
        val top = h * 0.12f + lift * 1.5f
        drawLine(primary, Offset(fx, top), Offset(fx, h * 0.8f), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
        for (k in -1..1) drawLine(primary, Offset(fx + k * w * 0.05f, top), Offset(fx + k * w * 0.05f, top + h * 0.16f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
    }
}

// ---------------------------------------------------------------- Weight chart

/**
 * Last 30 days of logged weight as a line that draws itself, with a soft fill, a dot on the
 * latest value and a dashed target line. Time runs right to left in Persian.
 */
@Composable
fun WeightChart(checkins: List<Checkin>, targetKg: Double?, today: String, modifier: Modifier = Modifier) {
    val end = runCatching { LocalDate.parse(today) }.getOrNull() ?: return
    val start = end.minusDays(29)
    val points = checkins.mapNotNull { c ->
        val d = runCatching { LocalDate.parse(c.localDate) }.getOrNull() ?: return@mapNotNull null
        val w = c.weightKg ?: return@mapNotNull null
        if (d.isBefore(start) || d.isAfter(end)) null else (d.toEpochDay() - start.toEpochDay()).toFloat() to w.toFloat()
    }.sortedBy { it.first }
    if (points.isEmpty()) return
    val still = LocalPerf.current.reduceMotion
    val draw = remember(points) { Animatable(if (still) 1f else 0f) }
    LaunchedEffect(points) { if (!still) draw.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val line = MaterialTheme.colorScheme.primary
    val target = MaterialTheme.colorScheme.tertiary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val values = points.map { it.second } + listOfNotNull(targetKg?.toFloat())
    val minY = (values.min() - 1f)
    val maxY = (values.max() + 1f)

    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val w = size.width
            val h = size.height
            fun x(day: Float) = (day / 29f * w).let { if (rtl) w - it else it }
            fun y(kg: Float) = h - (kg - minY) / (maxY - minY) * h
            repeat(4) { i -> drawLine(grid.copy(alpha = 0.5f), Offset(0f, h * i / 3f), Offset(w, h * i / 3f), strokeWidth = 1f) }
            targetKg?.let {
                drawLine(target, Offset(0f, y(it.toFloat())), Offset(w, y(it.toFloat())), strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
            val path = Path()
            points.forEachIndexed { i, (d, kg) -> if (i == 0) path.moveTo(x(d), y(kg)) else path.lineTo(x(d), y(kg)) }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * draw.value, partial, true)
            if (points.size > 1 && draw.value > 0.98f) {
                val fill = Path().apply {
                    addPath(path)
                    lineTo(x(points.last().first), h)
                    lineTo(x(points.first().first), h)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.22f), Color.Transparent)))
            }
            drawPath(partial, line, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            val (ld, lkg) = points.last()
            if (draw.value > 0.95f) {
                drawCircle(Color.White, 6.dp.toPx(), Offset(x(ld), y(lkg)))
                drawCircle(line, 4.dp.toPx(), Offset(x(ld), y(lkg)))
            } else if (points.size == 1) {
                drawCircle(line, 4.dp.toPx() * draw.value, Offset(x(ld), y(lkg)))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text(jalaliLabel(start.toString()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            targetKg?.let {
                Box(Modifier.size(width = 14.dp, height = 2.dp).background(target, RoundedCornerShape(1.dp)))
                Spacer(Modifier.width(4.dp))
                Text("هدف ${it.faDecimal()}", style = MaterialTheme.typography.labelSmall, color = target)
                Spacer(Modifier.weight(1f))
            }
            Text("امروز", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
