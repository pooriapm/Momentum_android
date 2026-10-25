package com.pooriapm.momentum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.data.Meal
import com.pooriapm.momentum.data.MealOption
import com.pooriapm.momentum.data.Nutrition
import com.pooriapm.momentum.data.PlanDay
import kotlin.math.abs

/**
 * Changing a meal to another option the plan already offers for it (the server only accepts those).
 * Before anything is saved the user sees what changes: the meal, the day's planned totals and the
 * day's shopping. Confirming selects the option; it never marks the meal as eaten.
 */

/** Ingredient amount differences new − old, per name and unit; a shared cook scales the old side. */
fun shoppingDelta(old: MealOption, oldPortions: Int, new: MealOption): List<Triple<String, String, Double>> {
    val amounts = LinkedHashMap<Pair<String, String>, Double>()
    old.ingredients.forEach { val k = it.name.trim() to it.unit.lowercase(); amounts[k] = (amounts[k] ?: 0.0) - it.amount * oldPortions }
    new.ingredients.forEach { val k = it.name.trim() to it.unit.lowercase(); amounts[k] = (amounts[k] ?: 0.0) + it.amount }
    return amounts.filterValues { abs(it) > 1e-6 }.map { (k, v) -> Triple(k.first, k.second, v) }
}

private fun PlanDay.plannedTotals(swap: Pair<String, MealOption>? = null): Nutrition {
    var cal = 0.0; var p = 0.0; var c = 0.0; var f = 0.0
    meals.forEach { meal ->
        val n = (if (swap?.first == meal.slotKey) swap.second else meal.chosenOption)?.nutrition ?: return@forEach
        cal += n.calories; p += n.proteinG; c += n.carbsG; f += n.fatG
    }
    return Nutrition(cal, p, c, f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealChangeSheet(state: AppState, day: PlanDay, meal: Meal, target: MealOption, onDismiss: () -> Unit, onDone: () -> Unit) {
    val current = meal.chosenOption ?: return
    val colors = MaterialTheme.colorScheme
    val cook = state.sharedCooks.activeFor(meal, day.localDate)
    val before = day.plannedTotals()
    val after = day.plannedTotals(meal.slotKey to target)
    val delta = shoppingDelta(current, cook?.portions ?: 1, target)
    val pending = state.mealPending(day.localDate, meal.slotKey)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("بررسی تغییر", style = MaterialTheme.typography.headlineSmall)
                Text("${meal.title} · ${jalaliLabel(day.localDate)}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            Compare("غذا", current.title, target.title)
            Compare("کالری این وعده", current.nutrition.calories.faRounded(), target.nutrition.calories.faRounded())
            Compare("پروتئین این وعده", "${current.nutrition.proteinG.faRounded()} گرم", "${target.nutrition.proteinG.faRounded()} گرم")

            HorizontalDivider()
            Text("برنامهٔ امروز", style = MaterialTheme.typography.titleMedium)
            Compare("کالری برنامه‌ریزی‌شده", before.calories.faRounded(), after.calories.faRounded(), "هدف ${day.targets.calories.faRounded()}")
            Compare("پروتئین", "${before.proteinG.faRounded()} گرم", "${after.proteinG.faRounded()} گرم", "هدف ${day.targets.proteinG.faRounded()}")

            HorizontalDivider()
            Text("اثر بر فهرست خرید امروز", style = MaterialTheme.typography.titleMedium)
            if (cook != null) {
                Text("پخت مشترک ${cook.portions.fa()} سهمی برای غذای فعلی است و بعد از تغییر حساب نمی‌شود.",
                    style = MaterialTheme.typography.bodyMedium, color = colors.secondary)
            }
            if (delta.isEmpty()) {
                Text("مواد لازم تغییری نمی‌کند.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            } else {
                delta.forEach { (name, unit, amount) ->
                    Text("${if (amount > 0) "+" else "−"} ${shoppingAmount(abs(amount), unit)} $name", style = MaterialTheme.typography.bodyMedium)
                }
                Text("خریدهای ثبت‌شده تغییر نمی‌کنند.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            target.warnings.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.secondary) }
            Text("تغییر غذا، وعده را «خورده‌شده» ثبت نمی‌کند.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)

            Button(
                onClick = { state.changeMeal(meal, day.localDate, target.optionKey); onDone() },
                enabled = !pending && meal.completionStatus == "planned",
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("تایید تغییر") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("انصراف") }
        }
    }
}

@Composable
private fun Compare(label: String, from: String, to: String, note: String? = null) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
        Column(Modifier.weight(1.3f)) {
            Text(if (from == to) to else "$from ← $to", style = MaterialTheme.typography.bodyMedium)
            note?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) }
        }
    }
}
