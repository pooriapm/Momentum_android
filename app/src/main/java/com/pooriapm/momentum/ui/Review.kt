package com.pooriapm.momentum.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.data.Dashboard
import com.pooriapm.momentum.data.PlanHistoryItem
import java.time.LocalDate

// Weekly review: one plan week at a time, from the server's weekly counts plus the day's reports.
// Counts are of what was logged; an unlogged meal is "not logged", not "missed".

private data class WeekRange(val start: String, val end: String)

/** Plan week [week] (1-based, as the server numbers it), cut at today. */
private fun weekRange(planStart: String, planEnd: String, today: String, week: Int): WeekRange {
    val start = LocalDate.parse(planStart).plusDays((week - 1) * 7L)
    val end = minOf(start.plusDays(6), LocalDate.parse(planEnd), LocalDate.parse(today))
    return WeekRange(start.toString(), end.toString())
}

@Composable
fun WeeklyReview(state: AppState, dashboard: Dashboard, modifier: Modifier = Modifier) {
    val plan = dashboard.plan ?: return
    val series = dashboard.progressSeries
    if (series.isEmpty()) {
        com.pooriapm.momentum.ui.components.InfoCard("مرور هفته", modifier) { Text("اطلاعات هفتهٔ این برنامه هنوز دریافت نشده است.") }
        return
    }
    var selected by rememberSaveable { mutableIntStateOf(series.last().week) }
    val point = series.firstOrNull { it.week == selected } ?: series.last()
    val range = weekRange(plan.validFrom, plan.validTo, dashboard.localDate, point.week)
    val inWeek = { date: String -> date >= range.start && date <= range.end }

    val reports = dashboard.recentCheckins.filter { inWeek(it.localDate) }
    val sleep = reports.mapNotNull { it.sleepMinutes }
    val energy = reports.mapNotNull { it.energyScore }
    val weights = reports.filter { it.weightKg != null }.sortedBy { it.localDate }
    val cooks = state.sharedCooks.filter { inWeek(it.date) }
    val prepared = state.prepared.count { inWeek(it.substringBefore('|')) }
    val days = (LocalDate.parse(range.end).toEpochDay() - LocalDate.parse(range.start).toEpochDay() + 1).toInt()

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (series.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                series.forEach { p ->
                    FilterChip(selected = p.week == point.week, onClick = { selected = p.week }, label = { Text("هفتهٔ ${p.week.fa()}") })
                }
            }
        }
        val meals = if (point.mealsPlanned > 0) RingState.Progress(point.mealsCompleted.toDouble(), point.mealsPlanned.toDouble(),
            "${point.mealsCompleted.fa()} از ${point.mealsPlanned.fa()} ثبت شده") else RingState.Unknown("وعده‌ای برنامه‌ریزی نشده")
        val workouts = if (point.workoutsPlanned > 0) RingState.Progress(point.workoutsCompleted.toDouble(), point.workoutsPlanned.toDouble(),
            "${point.workoutsCompleted.fa()} از ${point.workoutsPlanned.fa()} جلسه") else RingState.Rest("تمرینی برنامه‌ریزی نشده")
        val lines = buildList {
            add(if (reports.isEmpty()) "گزارش روزانه: ثبت نشده" else "گزارش روزانه: ${reports.size.fa()} از ${days.fa()} روز")
            if (sleep.isNotEmpty()) add("میانگین خواب: ${(sleep.average() / 60).faDecimal()} ساعت (${sleep.size.fa()} گزارش)")
            if (energy.isNotEmpty()) add("میانگین انرژی: ${energy.average().faDecimal()} از ۵")
            if (weights.size >= 2) {
                val diff = weights.last().weightKg!! - weights.first().weightKg!!
                add("وزن: ${weights.first().weightKg!!.faDecimal()} ← ${weights.last().weightKg!!.faDecimal()} کیلوگرم" +
                    if (kotlin.math.abs(diff) < 0.05) " (بدون تغییر)" else "")
            } else if (weights.size == 1) add("وزن: ${weights.first().weightKg!!.faDecimal()} کیلوگرم (یک ثبت)")
            if (cooks.isNotEmpty()) add("پخت مشترک: ${cooks.size.fa()} وعده")
            if (prepared > 0) add("آماده‌سازی: ${prepared.fa()} وعده")
        }
        OrbitHero(
            title = "مرور هفتهٔ ${point.week.fa()}",
            subtitle = "${jalaliLabel(range.start)} تا ${jalaliLabel(range.end)}" + if (point.partial) " · هفته ادامه دارد" else "",
            period = "این هفته",
            rings = listOf(OrbitRing("وعده‌ها", OrbitColors.Food, meals), OrbitRing("تمرین", OrbitColors.Training, workouts)),
            footer = lines.joinToString("\n"),
        )
    }
}

/**
 * Re-entry: shown once per ended plan, only when the dashboard really says the period is over (no
 * active plan, the last one ended before today, no next plan yet). A normal return shows nothing.
 */
fun endedPlan(dashboard: Dashboard): PlanHistoryItem? {
    if (dashboard.plan != null || dashboard.profile.onboardingStatus != "complete") return null
    if (dashboard.planHistory.any { it.validFrom >= dashboard.localDate }) return null
    return dashboard.planHistory.filter { it.validTo < dashboard.localDate }.maxByOrNull { it.validTo }
}

@Composable
fun PlanEndedNotice(ended: PlanHistoryItem, onReview: () -> Unit, onHistory: () -> Unit, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("دورهٔ برنامه تمام شده است") },
        text = { Text("برنامهٔ قبلی تا ${jalaliLabel(ended.validTo)} بود. سوابق شما محفوظ است.") },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row {
                    TextButton(onClick = onReview) { Text("مرور اطلاعات") }
                    TextButton(onClick = onHistory) { Text("مشاهده سوابق") }
                }
                TextButton(onClick = onClose) { Text("بستن") }
            }
        },
    )
}
