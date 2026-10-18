package com.pooriapm.momentum.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pooriapm.momentum.data.WorkoutExerciseLog
import com.pooriapm.momentum.data.WorkoutSession
import com.pooriapm.momentum.ui.components.MomentumField
import com.pooriapm.momentum.ui.components.SegmentedPicker
import com.pooriapm.momentum.ui.theme.LocalGlow
import com.pooriapm.momentum.ui.theme.hueBackground
import kotlinx.coroutines.delay
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val SkipReasons = listOf("درد یا ناراحتی", "وسیله ندارم", "وقت ندارم", "خیلی سخت بود")
private val StopReasons = listOf("وقت ندارم", "خسته‌ام", "حالم خوب نیست", "بعداً ادامه می‌دهم")

/**
 * Today's workout, one exercise at a time: tap set circles as you go (a rest timer starts after
 * each), swap to the planned alternative or skip with a reason, report pain, then finish. Every
 * step is saved on the server immediately, so closing and reopening resumes where you were.
 */
@Composable
fun WorkoutRunner(state: AppState, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        val glow = LocalGlow.current
        // The dialog has its own window: dark status-bar icons on the light hue (light ones in dark mode).
        val dark = isSystemInDarkTheme()
        val view = LocalView.current
        LaunchedEffect(dark) {
            (view.parent as? DialogWindowProvider)?.window?.let {
                WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !dark
            }
        }
        val session = state.workout
        Box(Modifier.fillMaxSize().hueBackground(glow).safeDrawingPadding()) {
            when {
                session == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                session.status == "completed" -> Finished(session, onClose)
                session.status == "stopped" -> Stopped(session, onClose)
                else -> Running(state, session, onClose)
            }
        }
    }
}

@Composable
private fun Running(state: AppState, session: WorkoutSession, onClose: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    var restEndsAt by remember { mutableLongStateOf(0L) }
    var restTotal by remember { mutableIntStateOf(0) }
    var skipping by remember { mutableStateOf<WorkoutExerciseLog?>(null) }
    var stopping by rememberSaveable { mutableStateOf(false) }
    var painOpen by rememberSaveable { mutableStateOf(false) }
    val done = session.exercises.count { it.status == "completed" || it.status == "skipped" }

    // An exercise whose sets are all ticked is closed on the server, so "finish" can be enabled.
    LaunchedEffect(session) {
        session.exercises.firstOrNull { it.doneSets == it.plannedSets && it.status != "completed" && it.status != "skipped" }
            ?.let { state.workoutStep("complete_exercise", it.exerciseKey) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "بستن") }
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(session.workoutTitle, style = MaterialTheme.typography.titleMedium)
                Text("${done.fa()} از ${session.exercises.size.fa()} حرکت", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { painOpen = true }) {
                Icon(Icons.Outlined.Healing, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("درد دارم")
            }
        }
        LinearProgressIndicator(
            progress = { if (session.exercises.isEmpty()) 0f else done.toFloat() / session.exercises.size },
            strokeCap = StrokeCap.Round,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(6.dp),
        )
        RestTimer(restEndsAt, restTotal) { restEndsAt = 0L }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(session.exercises, key = { it.id }) { exercise ->
                ExerciseCard(
                    exercise = exercise,
                    busy = state.workoutBusy,
                    onSet = { number, complete ->
                        state.workoutStep("update_set", exercise.exerciseKey, number, buildJsonObject { put("completed", complete) })
                        if (complete) {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            if (exercise.plannedRestSeconds > 0) {
                                restTotal = exercise.plannedRestSeconds
                                restEndsAt = System.currentTimeMillis() + exercise.plannedRestSeconds * 1000L
                            }
                        }
                    },
                    onSwap = {
                        exercise.plannedSubstituteExerciseId?.let { id ->
                            state.workoutStep("substitute_exercise", exercise.exerciseKey, values = buildJsonObject { put("exercise_id", id) })
                        }
                    },
                    onSkip = { skipping = exercise },
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { state.workoutStep("finish") },
                enabled = session.canFinish && !state.workoutBusy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(if (session.canFinish) "پایان تمرین" else "حرکت‌ها را انجام بده یا رد کن") }
            TextButton(onClick = { stopping = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("توقف تمرین") }
        }
    }

    skipping?.let { exercise ->
        ReasonDialog("چرا این حرکت را رد می‌کنی؟", SkipReasons, onDismiss = { skipping = null }) { reason ->
            skipping = null
            state.workoutStep("skip_exercise", exercise.exerciseKey, values = buildJsonObject { put("reason", reason) })
        }
    }
    if (stopping) {
        ReasonDialog("توقف تمرین امروز", StopReasons, onDismiss = { stopping = false }) { reason ->
            stopping = false
            state.workoutStep("stop", values = buildJsonObject { put("reason", reason) })
        }
    }
    if (painOpen) PainDialog(onDismiss = { painOpen = false }) { severity, area ->
        painOpen = false
        state.workoutStep("report_pain", values = buildJsonObject { put("severity", severity); put("area", area) })
    }
}

@Composable
private fun RestTimer(endsAt: Long, total: Int, onDone: () -> Unit) {
    var left by remember(endsAt) { mutableIntStateOf(((endsAt - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)) }
    LaunchedEffect(endsAt) {
        while (endsAt > 0 && left > 0) {
            delay(250)
            left = ((endsAt - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)
        }
        if (endsAt > 0) onDone()
    }
    AnimatedVisibility(endsAt > 0 && left > 0, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("استراحت", style = MaterialTheme.typography.labelLarge)
                    LinearProgressIndicator(
                        progress = { if (total == 0) 0f else left.toFloat() / total },
                        color = MaterialTheme.colorScheme.tertiary,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text("${(left / 60).fa()}:${(left % 60).toString().padStart(2, '0').fa()}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onDone) { Text("رد کردن") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExerciseCard(
    exercise: WorkoutExerciseLog,
    busy: Boolean,
    onSet: (Int, Boolean) -> Unit,
    onSwap: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val skipped = exercise.status == "skipped"
    val completed = exercise.status == "completed"
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${(exercise.position + 1).fa()}. ${exercise.substituteName ?: exercise.plannedName}",
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (skipped) TextDecoration.LineThrough else null,
                    color = if (skipped) colors.onSurfaceVariant else colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (completed) Icon(Icons.Rounded.Check, contentDescription = "انجام شد", tint = colors.tertiary)
            }
            Text(
                "${exercise.plannedSets.fa()} ست × ${rangeLabel(exercise.plannedReps)} · ${exercise.plannedRestSeconds.fa()} ثانیه استراحت" +
                    (exercise.substituteName?.let { " · جایگزین ${exercise.plannedName}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            if (skipped) {
                Text("رد شد${exercise.skipReason?.let { " · $it" } ?: ""}", style = MaterialTheme.typography.bodySmall, color = colors.outline)
                return@Column
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                exercise.sets.sortedBy { it.setNumber }.forEach { set ->
                    val on = set.status == "completed"
                    val fill by animateColorAsState(if (on) colors.tertiary else colors.surface, label = "set")
                    Box(
                        Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(fill)
                            .border(1.5.dp, if (on) colors.tertiary else colors.outline, CircleShape)
                            .clickable(enabled = !busy && !completed, role = Role.Checkbox) { onSet(set.setNumber, !on) }
                            .semantics { contentDescription = "ست ${set.setNumber.fa()}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (on) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onTertiary)
                        else Text(set.setNumber.fa(), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (!completed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (exercise.plannedSubstituteExerciseId != null && exercise.substituteExerciseId == null) {
                        OutlinedButton(onClick = onSwap, enabled = !busy) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("حرکت جایگزین")
                        }
                    }
                    TextButton(onClick = onSkip, enabled = !busy) { Text("رد کردن حرکت") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReasonDialog(title: String, reasons: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    var picked by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { r -> FilterChip(selected = picked == r, onClick = { picked = r }, label = { Text(r) }) }
            }
        },
        confirmButton = { TextButton(onClick = { picked?.let(onPick) }, enabled = picked != null) { Text("ثبت") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun PainDialog(onDismiss: () -> Unit, onReport: (Int, String) -> Unit) {
    var severity by remember { mutableIntStateOf(2) }
    var area by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("گزارش درد") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("شدت درد (۱ خفیف تا ۵ شدید)", style = MaterialTheme.typography.bodyMedium)
                SegmentedPicker((1..5).map { it.fa() }, severity - 1) { severity = it + 1 }
                MomentumField(value = area, onValueChange = { if (it.length <= 160) area = it }, label = "کجای بدن؟", placeholder = "مثلاً زانوی راست")
                if (severity >= 4) {
                    Text("با درد شدید تمرین امروز متوقف می‌شود. اگر درد ادامه داشت با پزشک مشورت کن.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onReport(severity, area.trim()) }, enabled = area.isNotBlank()) { Text("ثبت") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun Finished(session: WorkoutSession, onClose: () -> Unit) {
    val sets = session.exercises.sumOf { it.doneSets }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val pop = remember { Animatable(0.3f) }
        LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMediumLow)) }
        Box(Modifier.size(96.dp).scale(pop.value).clip(CircleShape).background(MaterialTheme.colorScheme.tertiary), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("تمرین امروز تمام شد", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "${sets.fa()} ست در ${session.exercises.count { it.status == "completed" }.fa()} حرکت. آفرین!",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("بازگشت") }
    }
}

@Composable
private fun Stopped(session: WorkoutSession, onClose: () -> Unit) {
    val severePain = (session.painSeverity ?: 0) >= 4
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("تمرین امروز متوقف شد", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            if (severePain) "به‌خاطر درد شدید تمرین را متوقف کردیم. استراحت کن؛ اگر درد ادامه داشت یا بدتر شد با پزشک مشورت کن."
            else "اشکالی ندارد؛ فردا دوباره ادامه بده.",
            style = MaterialTheme.typography.bodyLarge,
            color = if (severePain) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("بازگشت") }
    }
}
