package com.pooriapm.momentum.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import com.pooriapm.momentum.ui.components.MealStatusControl
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.data.Dashboard
import com.pooriapm.momentum.data.Meal
import com.pooriapm.momentum.data.Exercise
import com.pooriapm.momentum.data.Workout
import com.pooriapm.momentum.ui.components.InfoCard

@Composable
fun TodayTab(state: AppState, dashboard: Dashboard, openMembership: () -> Unit) {
    val day = dashboard.plan!!.day
    var openMeal by remember { mutableStateOf<Meal?>(null) }
    var checkinOpen by rememberSaveable { mutableStateOf(false) }
    var runnerOpen by rememberSaveable { mutableStateOf(false) }
    var prefsOpen by rememberSaveable { mutableStateOf(false) }
    var eatingOut by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(dashboard.localDate, day.workout != null) { if (day.workout != null) state.loadWorkout() }
    val done = day.meals.filter { it.completionStatus == "completed" }
    val eaten = done.sumOf { it.chosenOption?.nutrition?.calories ?: 0.0 }
    val next = day.meals.firstOrNull { it.completionStatus == "planned" }

    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("سلام، ${dashboard.profile.displayName}", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "${jalaliLabel(day.localDate)} · روز ${(day.dayIndex + 1).fa()} از ۳۰${dayTitle(day.title)?.let { " · $it" } ?: ""}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GuideButton(Guides.home)
            }
        }
        item { StreakCard(dashboard) }
        item { TargetCard(eaten, day.targets.calories, day.targets.proteinG, day.targets.carbsG, day.targets.fatG, done.size, day.meals.size) }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("قدم بعدی", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        when {
                            next != null -> "${next.title}${next.scheduledTime?.let { " · ساعت ${it.fa()}" } ?: ""}: ${next.chosenOption?.title.orEmpty()}"
                            day.workout != null && state.workout?.status != "completed" -> "وعده‌ها کامل شد؛ تمرین امروز مانده."
                            else -> "آفرین، برنامه امروز کامل شد."
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        item { WaterCard(state, (day.targets.waterMl ?: 2200.0).toInt()) }
        // The workout has its own section above the meals, so it is not lost at the end of the list.
        item { Text("تمرین امروز", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
        day.workout?.let { workout -> item { WorkoutCard(workout) { WorkoutAction(state) { runnerOpen = true } } } }
            ?: item { InfoCard("امروز روز استراحت است") { Text("بدن در روزهای استراحت قوی‌تر می‌شود. پیاده‌روی سبک کافی است.") } }
        item { PlanEndingCard(state, dashboard) { prefsOpen = true } }
        item { CheckinCard(dashboard.checkin != null) { checkinOpen = true } }
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("وعده‌های امروز", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(50)).clickable(role = Role.Button) { eatingOut = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BouncingPlate(Modifier.size(26.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("بیرون غذا می‌خورم", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        items(day.meals, key = { it.slotKey }) { meal -> MealCard(state, meal, day.localDate) { openMeal = meal } }
    }

    openMeal?.let { meal ->
        val fresh = day.meals.firstOrNull { it.slotKey == meal.slotKey } ?: meal
        MealSheet(state, fresh, day.localDate, editable = true) { openMeal = null }
    }
    if (checkinOpen) CheckinSheet(state) { checkinOpen = false }
    if (runnerOpen) WorkoutRunner(state) { runnerOpen = false }
    if (prefsOpen) PreferencesScreen(state) { prefsOpen = false }
    if (eatingOut) EatingOutSheet(dashboard.plan?.restaurantGuide.orEmpty()) { eatingOut = false }
}

@Composable
private fun TargetCard(eaten: Double, target: Double, protein: Double, carbs: Double, fat: Double, mealsDone: Int, mealsTotal: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(eaten.faRounded(), style = MaterialTheme.typography.displaySmall)
                Text(" از ${target.faRounded()} کالری", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
            }
            LinearProgressIndicator(
                progress = { (eaten / target).toFloat().coerceIn(0f, 1f) },
                // Neutral track: the M3 default (secondaryContainer) reads as dark red in dark mode.
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Macro("پروتئین", protein)
                Macro("کربوهیدرات", carbs)
                Macro("چربی", fat)
            }
            Text("${mealsDone.fa()} از ${mealsTotal.fa()} وعده ثبت شده", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Macro(label: String, grams: Double) {
    Column {
        Text("${grams.faRounded()} گرم", style = MaterialTheme.typography.titleMedium)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CheckinCard(done: Boolean, onOpen: () -> Unit) {
    OutlinedCardRow(
        icon = if (done) Icons.Outlined.CheckCircle else Icons.Outlined.WbSunny,
        title = if (done) "گزارش امروز ثبت شد" else "گزارش روزانه",
        subtitle = if (done) "می‌توانی دوباره ویرایشش کنی." else "انرژی، خواب و حالت؛ کمتر از یک دقیقه.",
        onClick = onOpen,
    )
}

@Composable
fun OutlinedCardRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MealCard(state: AppState, meal: Meal, localDate: String, editable: Boolean = true, onOpen: () -> Unit) {
    val option = meal.chosenOption
    val status = meal.completionStatus
    val skipped = status == "skipped"
    val busy = false // changes are instant; the control only blocks taps while saving
    val colors = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(containerColor = colors.surface),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onOpen),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    when (status) {
                        "completed" -> Icons.Outlined.CheckCircle
                        "skipped" -> Icons.Outlined.DoNotDisturbOn
                        else -> Icons.Outlined.Restaurant
                    },
                    contentDescription = null,
                    tint = when (status) {
                        "completed" -> colors.tertiary
                        "skipped" -> colors.outline
                        else -> colors.primary
                    },
                    modifier = Modifier.padding(top = 2.dp).size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(meal.title, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    option?.let {
                        Text(
                            it.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (skipped) colors.onSurfaceVariant else colors.onSurface,
                            textDecoration = if (skipped) TextDecoration.LineThrough else null,
                        )
                        Text(
                            "${it.nutrition.calories.faRounded()} کالری · پروتئین ${it.nutrition.proteinG.faRounded()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(meal.scheduledTime?.fa().orEmpty(), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    if (editable && option != null) {
                        MealStatusControl(
                            status = status,
                            busy = busy,
                            enabled = !state.mealPending(localDate, meal.slotKey),
                            onEat = { state.completeMeal(meal, localDate) },
                            onSkip = { state.skipMeal(meal, localDate) },
                            onUndo = { state.undoMeal(meal, localDate) },
                        )
                    }
                }
            }
            // Meals generated with alternatives show them as small cards; single-option meals don't.
            if (meal.options.size > 1) {
                OptionStrip(
                    meal = meal,
                    selectable = editable && status == "planned" && !state.mealPending(localDate, meal.slotKey),
                    onSelect = { state.chooseOption(meal, localDate, it) },
                )
            }
        }
    }
}

/**
 * The meal's alternatives as a row of small cards. The chosen one is highlighted; others can be
 * picked while the meal is still planned. Once eaten or skipped the row stays (no height jump) but
 * is locked, with the chosen option marked.
 */
@Composable
private fun OptionStrip(meal: Meal, selectable: Boolean, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val chosen = meal.chosenOption?.optionKey
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            if (selectable) "${meal.options.size.fa()} گزینه · یکی را انتخاب کن" else "${meal.options.size.fa()} گزینه",
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurfaceVariant,
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            meal.options.forEach { o ->
                val selected = o.optionKey == chosen
                val border by animateColorAsState(if (selected) colors.primary else colors.outlineVariant, label = "option-border")
                val container by animateColorAsState(if (selected) colors.primaryContainer else colors.surface, label = "option-bg")
                Column(
                    Modifier
                        .widthIn(min = 104.dp, max = 168.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(container)
                        .border(if (selected) 1.5.dp else 1.dp, border, RoundedCornerShape(14.dp))
                        .selectable(selected = selected, enabled = selectable || selected, role = Role.RadioButton) { onSelect(o.optionKey) }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .graphicsLayer { alpha = if (selectable || selected) 1f else 0.55f },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(o.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${o.nutrition.calories.faRounded()} کالری", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun WorkoutCard(workout: Workout, footer: (@Composable () -> Unit)? = null) {
    val index = rememberExerciseIndex()
    var guide by remember { mutableStateOf<Exercise?>(null) }
    val colors = MaterialTheme.colorScheme
    Card(colors = CardDefaults.cardColors(containerColor = colors.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(colors.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.FitnessCenter, contentDescription = null, tint = colors.secondary)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(workout.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${workout.durationMinutes.fa()} دقیقه · ${workout.exercises.size.fa()} حرکت · ${intensityLabel(workout.intensity)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            if (workout.warmup.isNotEmpty()) WorkoutNote("گرم کردن", workout.warmup)
            workout.exercises.forEachIndexed { i, exercise ->
                ExerciseRow(i, exercise, index?.find(exercise.exerciseKey, exercise.name)?.anim) { guide = exercise }
            }
            if (workout.cooldown.isNotEmpty()) WorkoutNote("سرد کردن", workout.cooldown)
            workout.safetyNote?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            footer?.invoke()
        }
    }
    guide?.let { e ->
        ExerciseSheet(
            ExercisePlanInfo(e.name, e.exerciseKey, e.sets, e.reps, e.restSeconds, e.equipment, e.intensityNote, e.substitution),
        ) { guide = null }
    }
}

/** One exercise in the card: its still animation, name and numbers; tap for the full guide. */
@Composable
private fun ExerciseRow(position: Int, exercise: Exercise, anim: String?, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(role = Role.Button, onClick = onOpen).padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExerciseAnimation(anim, Modifier.size(60.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${(position + 1).fa()}. ${exercise.name}", style = MaterialTheme.typography.titleSmall)
            Text(
                "${exercise.sets.fa()} ست × ${rangeLabel(exercise.reps)} · ${exercise.restSeconds.fa()} ثانیه استراحت",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Text("راهنما", style = MaterialTheme.typography.labelLarge, color = colors.primary, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
private fun WorkoutNote(title: String, items: List<String>) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
        Text(items.joinToString("، "), style = MaterialTheme.typography.bodyMedium)
    }
}

fun intensityLabel(value: String) = when (value) {
    "low" -> "سبک"
    "high" -> "سنگین"
    else -> "متوسط"
}

/** Today's workout status under its card: start, continue (with progress), or done. */
@Composable
private fun WorkoutAction(state: AppState, open: () -> Unit) {
    val session = state.workout
    val colors = MaterialTheme.colorScheme
    when {
        session?.status == "completed" -> Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.tertiaryContainer)
                .clickable(role = Role.Button, onClick = open).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.tertiary)
            Spacer(Modifier.width(10.dp))
            Text("تمرین امروز انجام شد", style = MaterialTheme.typography.titleSmall, color = colors.tertiary)
        }
        session?.status == "stopped" -> Text("تمرین امروز متوقف شد.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        session != null -> Button(onClick = open, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            val done = session.exercises.count { it.status == "completed" || it.status == "skipped" }
            Text("ادامه تمرین · ${done.fa()} از ${session.exercises.size.fa()}")
        }
        else -> Button(
            onClick = { state.startWorkout(open) },
            enabled = !state.workoutBusy && state.todayWorkoutKey() != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            if (state.workoutBusy) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) else Text("شروع تمرین")
        }
    }
}
