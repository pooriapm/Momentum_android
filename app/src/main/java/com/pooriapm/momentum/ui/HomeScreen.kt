package com.pooriapm.momentum.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.BuildConfig
import com.pooriapm.momentum.billing.BazaarBilling
import com.pooriapm.momentum.billing.BillingResult
import com.pooriapm.momentum.data.Dashboard
import com.pooriapm.momentum.data.Meal
import com.pooriapm.momentum.data.PlanDay

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("امروز", Icons.Outlined.Home),
    Tab("برنامه", Icons.Outlined.CalendarMonth),
    Tab("پیشرفت", Icons.AutoMirrored.Outlined.ShowChart),
    Tab("حساب من", Icons.Outlined.Person),
)

@Composable
fun HomeScreen(state: AppState, billing: BazaarBilling) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        val dashboard = state.dashboard
        Column(Modifier.fillMaxSize().padding(padding)) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            when {
                dashboard == null && state.loading -> Box { CircularProgressIndicator() }
                dashboard == null -> Box { Button(onClick = state::refresh) { Text("تلاش دوباره") } }
                tab == 3 -> MeTab(state, dashboard, billing)
                dashboard.plan == null -> NoPlanTab(state, dashboard)
                tab == 0 -> TodayTab(state, dashboard.plan.day, dashboard.profile.displayName)
                tab == 1 -> PlanTab(dashboard.plan.days, dashboard.localDate)
                else -> ProgressTab(dashboard)
            }
        }
    }
}

@Composable
private fun Box(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        content()
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun NoPlanTab(state: AppState, dashboard: Dashboard) {
    val context = LocalContext.current
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Section("برنامه‌ات هنوز آماده نیست") {
                Text(
                    if (dashboard.profile.onboardingStatus == "complete") "راه‌اندازی کامل است. ساخت برنامه چند دقیقه طول می‌کشد؛ بعداً سر بزن."
                    else "برای ساخت برنامه ۳۰ روزه، چند سؤال درباره هدف، غذا و تمرینت لازم است.",
                )
                if (dashboard.profile.onboardingStatus != "complete") {
                    Button(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.SITE_URL.trimEnd('/')}/fa/onboarding")))
                    }) { Text("شروع راه‌اندازی") }
                }
                OutlinedButton(onClick = state::refresh) { Text("به‌روزرسانی") }
            }
        }
    }
}

@Composable
private fun TodayTab(state: AppState, day: PlanDay, name: String) {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("سلام، $name", style = MaterialTheme.typography.headlineSmall)
            Text(day.title ?: "روز ${(day.dayIndex + 1).fa()} از ۳۰", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Section("هدف امروز") {
                Text("${day.targets.calories.faRounded()} کالری · ${day.targets.proteinG.faRounded()} گرم پروتئین")
            }
        }
        items(day.meals, key = { it.slotKey }) { meal -> MealCard(state, meal, day.localDate) }
        day.workout?.let { workout ->
            item {
                Section("تمرین امروز") {
                    Text(workout.title, fontWeight = FontWeight.Bold)
                    Text("${workout.durationMinutes.fa()} دقیقه · ${workout.exercises.size.fa()} حرکت")
                    workout.exercises.forEach { e ->
                        Text("• ${e.name}: ${e.sets.fa()} × ${e.reps.fa()} · ${e.restSeconds.fa()} ثانیه استراحت")
                    }
                    workout.safetyNote?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
private fun MealCard(state: AppState, meal: Meal, localDate: String) {
    val option = meal.chosenOption
    val done = meal.completionStatus == "completed"
    Section("${meal.title}${meal.scheduledTime?.let { " · ${it.fa()}" } ?: ""}") {
        option?.let {
            Text(it.title)
            Text(
                "${it.nutrition.calories.faRounded()} کالری · ${it.nutrition.proteinG.faRounded()} گرم پروتئین",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val busy = state.busyMeal == meal.slotKey
        if (done) {
            OutlinedButton(onClick = { state.toggleMeal(meal, localDate) }, enabled = !busy) { Text("برگرداندن ثبت") }
        } else {
            Button(onClick = { state.toggleMeal(meal, localDate) }, enabled = !busy) { Text("ثبت شد") }
        }
    }
}

@Composable
private fun PlanTab(days: List<PlanDay>, today: String) {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(days, key = { it.dayIndex }) { day ->
            Section("${jalaliLabel(day.localDate)}${if (day.localDate == today) " · امروز" else ""}") {
                day.title?.let { Text(it) }
                day.meals.forEach { meal -> Text("• ${meal.title}: ${meal.chosenOption?.title.orEmpty()}") }
                day.workout?.let { Text("تمرین: ${it.title} · ${it.durationMinutes.fa()} دقیقه", color = MaterialTheme.colorScheme.primary) }
                    ?: Text("روز استراحت", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ProgressTab(dashboard: Dashboard) {
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Section("پایبندی هفتگی") {
                if (dashboard.progressSeries.isEmpty()) Text("بعد از چند روز ثبت، روندت اینجا دیده می‌شود.")
                dashboard.progressSeries.forEach { point ->
                    Text("هفته ${point.week.fa()}${if (point.partial) " · ناقص" else ""}: ${point.adherence.fa()}٪")
                    LinearProgressIndicator(progress = { point.adherence / 100f }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        val weights = dashboard.recentCheckins.filter { it.weightKg != null }
        if (weights.isNotEmpty()) {
            item {
                Section("وزن ثبت‌شده") {
                    weights.take(7).forEach { Text("${jalaliLabel(it.localDate)}: ${it.weightKg!!.toString().fa()} کیلوگرم") }
                }
            }
        }
    }
}

@Composable
private fun MeTab(state: AppState, dashboard: Dashboard, billing: BazaarBilling) {
    var buying by rememberSaveable { mutableStateOf(false) }
    val entitlement = dashboard.entitlementUsage?.entitlement
    val active = entitlement?.status == "active"
    LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Section("عضویت Momentum") {
                Text(
                    when {
                        active && entitlement?.source == "gift" -> "هدیه ماه اول فعال است تا ${jalaliLabel(entitlement.periodEnd)}."
                        active -> "عضویت فعال است تا ${jalaliLabel(entitlement!!.periodEnd)}."
                        else -> "عضویت فعال نیست. هر پرداخت ۳۰ روز برنامه ماهانه، جایگزینی وعده، فهرست خرید و روند پیشرفت را باز می‌کند."
                    },
                )
                Text("۴۹۰٬۰۰۰ تومان برای ۳۰ روز · پرداخت از کافه‌بازار · بدون تمدید خودکار", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    enabled = !buying,
                    onClick = {
                        buying = true
                        billing.buyMonth { result ->
                            buying = false
                            when (result) {
                                is BillingResult.Granted -> { state.notice = "پرداخت تأیید شد و عضویت ۳۰ روزه فعال شد."; state.refresh() }
                                BillingResult.Cancelled -> state.notice = "خرید لغو شد."
                                is BillingResult.Failed -> state.error = result.message
                            }
                        }
                    },
                ) { Text(if (active) "تمدید ۳۰ روز" else "خرید عضویت") }
                state.notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = state::refresh) { Text("به‌روزرسانی") }
                OutlinedButton(onClick = state::signOut) { Text("خروج از حساب") }
            }
        }
        item {
            Text(state.api.session?.email.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
