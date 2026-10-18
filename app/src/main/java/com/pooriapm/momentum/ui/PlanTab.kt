package com.pooriapm.momentum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.data.Dashboard
import com.pooriapm.momentum.data.Meal
import com.pooriapm.momentum.ui.components.InfoCard

/** The plan one day at a time: a progress ring with day arrows (no 30-item strip), then that day. */
@Composable
fun PlanTab(state: AppState, dashboard: Dashboard) {
    val plan = dashboard.plan!!
    val days = plan.days.ifEmpty { listOf(plan.day) }
    val todayIndex = days.indexOfFirst { it.localDate == dashboard.localDate }.coerceAtLeast(0)
    var selected by rememberSaveable { mutableIntStateOf(todayIndex) }
    var openMeal by remember { mutableStateOf<Meal?>(null) }
    val day = days[selected.coerceIn(days.indices)]
    val isToday = day.localDate == dashboard.localDate

    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(plan.name, style = MaterialTheme.typography.headlineSmall)
            Text("${jalaliLabel(plan.validFrom)} تا ${jalaliLabel(plan.validTo)}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { ShoppingEntry(state, plan, dashboard.localDate) }
        item(key = "navigator") {
            DayNavigator(
                index = selected.coerceIn(days.indices),
                count = days.size,
                todayIndex = todayIndex,
                date = day.localDate,
                training = day.workout != null,
                onChange = { selected = it },
            )
        }
        item(key = "targets") {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                dayTitle(day.title)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Text("هدف روز: ${day.targets.calories.faRounded()} کالری · ${day.targets.proteinG.faRounded()} گرم پروتئین",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "history") { PlanHistoryCard(dashboard.planHistory) }
        items(day.meals, key = { "${day.dayIndex}-${it.slotKey}" }) { meal ->
            MealCard(state, meal, day.localDate, editable = isToday) { openMeal = meal }
        }
        day.workout?.let { item { WorkoutCard(it) } }
            ?: item { InfoCard("روز استراحت") { Text("برای این روز تمرینی برنامه‌ریزی نشده است.") } }
    }
    openMeal?.let { meal ->
        val fresh = day.meals.firstOrNull { it.slotKey == meal.slotKey } ?: meal
        MealSheet(state, fresh, day.localDate, editable = isToday) { openMeal = null }
    }
}
