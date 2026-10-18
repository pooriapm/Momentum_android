package com.pooriapm.momentum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.pooriapm.momentum.ui.components.InfoCard
import java.time.LocalDate

private val weekdayNames = listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")

/** Calendar strip of the 30 days (like a week view in fitness apps) and the chosen day below it. */
@Composable
fun PlanTab(state: AppState, dashboard: Dashboard) {
    val plan = dashboard.plan!!
    val days = plan.days.ifEmpty { listOf(plan.day) }
    val todayIndex = days.indexOfFirst { it.localDate == dashboard.localDate }.coerceAtLeast(0)
    var selected by rememberSaveable { mutableIntStateOf(todayIndex) }
    var openMeal by remember { mutableStateOf<Meal?>(null) }
    val strip = rememberLazyListState()
    LaunchedEffect(Unit) { strip.scrollToItem((todayIndex - 2).coerceAtLeast(0)) }
    val day = days[selected.coerceIn(days.indices)]
    val isToday = day.localDate == dashboard.localDate

    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(plan.name, style = MaterialTheme.typography.headlineSmall)
            Text("${jalaliLabel(plan.validFrom)} تا ${jalaliLabel(plan.validTo)}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { ShoppingEntry(plan, dashboard.localDate) }
        item {
            LazyRow(state = strip, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                items(days.size) { index ->
                    val d = days[index]
                    val date = runCatching { LocalDate.parse(d.localDate) }.getOrNull()
                    val chosen = index == selected
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (chosen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (chosen) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.width(64.dp).selectable(chosen, role = Role.Tab) { selected = index },
                    ) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(date?.let { weekdayNames[it.dayOfWeek.value - 1].take(1) } ?: "", style = MaterialTheme.typography.labelMedium)
                            Text((isoToJalali(d.localDate)?.third ?: (index + 1)).fa(), style = MaterialTheme.typography.titleLarge)
                            Text(if (d.workout != null) "تمرین" else "استراحت", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        item {
            val date = runCatching { LocalDate.parse(day.localDate) }.getOrNull()
            Text(
                "${date?.let { weekdayNames[it.dayOfWeek.value - 1] } ?: ""} ${jalaliLabel(day.localDate)}${if (isToday) " · امروز" else ""}",
                style = MaterialTheme.typography.titleLarge,
            )
            dayTitle(day.title)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Text("هدف: ${day.targets.calories.faRounded()} کالری · ${day.targets.proteinG.faRounded()} گرم پروتئین",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
