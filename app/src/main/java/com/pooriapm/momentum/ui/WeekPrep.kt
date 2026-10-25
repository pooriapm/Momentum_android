package com.pooriapm.momentum.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Kitchen
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.data.Meal
import com.pooriapm.momentum.data.Plan
import java.time.LocalDate

/**
 * Week prep: the cooked dishes of the next 7 plan days as prep tasks, plus the ingredients that come
 * back in several of them (worth preparing once). A task is "prepared" on its own: it is not bought
 * (shopping) and not eaten (meal status), and ticking it changes neither. The plan has no storage
 * times, so the view never says how long cooked food keeps or suggests cooking days ahead.
 */
data class PrepTask(val date: String, val meal: Meal, val portions: Int) {
    val key get() = prepKey(date, meal)
}

fun prepKey(date: String, meal: Meal) = "$date|${meal.slotKey}|${meal.chosenOption?.optionKey}"

private const val PREP_DAYS = 7L

/** Cooked dishes (two or more ingredients) from today for a week, skipping meals already logged. */
fun prepTasks(plan: Plan, today: String, cooks: List<SharedCook>): List<PrepTask> {
    val end = LocalDate.parse(today).plusDays(PREP_DAYS - 1).toString()
    return plan.days.filter { it.localDate in today..end }.sortedBy { it.localDate }.flatMap { day ->
        day.meals.filter { (it.chosenOption?.ingredients?.size ?: 0) >= 2 && it.completionStatus == "planned" }
            .map { PrepTask(day.localDate, it, cooks.activeFor(it, day.localDate)?.portions ?: 1) }
    }
}

/** An ingredient that several tasks share, with the week's total (shared cooks included). */
data class SharedIngredient(val name: String, val unit: String, val total: Double, val uses: Int)

fun sharedIngredients(tasks: List<PrepTask>): List<SharedIngredient> =
    tasks.flatMap { t -> t.meal.chosenOption!!.ingredients.map { Triple(it, t.portions, t.key) } }
        .groupBy { (i, _, _) -> i.name.trim() to i.unit.lowercase() }
        .mapNotNull { (k, rows) ->
            val uses = rows.map { it.third }.distinct().size
            if (uses < 2) null else SharedIngredient(k.first, k.second, rows.sumOf { (i, p, _) -> i.amount * p }, uses)
        }
        .sortedByDescending { it.uses }

/** Quiet entry on the Plan tab, next to the shopping list. */
@Composable
fun WeekPrepEntry(state: AppState, plan: Plan, today: String) {
    var open by rememberSaveable { mutableStateOf(false) }
    val tasks = prepTasks(plan, today, state.sharedCooks)
    val done = tasks.count { it.key in state.prepared }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { open = true },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Kitchen, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("آماده‌سازی هفته", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (tasks.isEmpty()) "غذای پختنی در هفتهٔ پیش رو نیست"
                    else "${done.fa()} از ${tasks.size.fa()} وعده آماده شده · تا ${jalaliLabel(tasks.last().date)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) WeekPrepSheet(state, tasks) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekPrepSheet(state: AppState, tasks: List<PrepTask>, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shared = sharedIngredients(tasks)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("آماده‌سازی هفته", style = MaterialTheme.typography.headlineSmall)
                Text("«آماده شد» فقط برای برنامه‌ریزی خودتان است؛ خرید یا خوردن وعده را ثبت نمی‌کند.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (tasks.isEmpty()) {
                Text("در هفت روز پیش رو غذای پختنیِ ثبت‌نشده‌ای نیست.", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            if (shared.isNotEmpty()) {
                Text("مواد تکراری هفته", style = MaterialTheme.typography.titleMedium)
                Text("می‌توانید این‌ها را یک‌جا آماده کنید. زمان نگهداری در برنامه نیست؛ به روش مطمئن خودتان نگه دارید.",
                    style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                shared.forEach {
                    Row(Modifier.fillMaxWidth()) {
                        Text(it.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${shoppingAmount(it.total, it.unit)} · ${it.uses.fa()} وعده", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
            tasks.groupBy { it.date }.forEach { (date, dayTasks) ->
                Text(jalaliLabel(date), style = MaterialTheme.typography.titleMedium)
                dayTasks.forEach { task -> PrepRow(task, task.key in state.prepared) { state.togglePrepared(task.key) } }
            }
        }
    }
}

@Composable
private fun PrepRow(task: PrepTask, prepared: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (prepared) colors.tertiary else colors.surface, label = "prep-fill")
    val option = task.meal.chosenOption ?: return
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Checkbox, onClick = onToggle).padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(fill).border(1.5.dp, if (prepared) colors.tertiary else colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (prepared) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onTertiary, modifier = Modifier.size(16.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(option.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${task.meal.title}" + (if (task.portions > 1) " · پخت مشترک ${task.portions.fa()} سهم" else "") +
                    // Cooking time does not grow linearly with the amount, so it is shown for one share only.
                    option.recipe?.takeIf { task.portions == 1 }?.let { " · ${(it.prepMinutes + it.cookMinutes).fa()} دقیقه" }.orEmpty(),
                style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant,
            )
        }
        Text(if (prepared) "آماده شد" else "", style = MaterialTheme.typography.labelMedium, color = colors.tertiary)
    }
}
