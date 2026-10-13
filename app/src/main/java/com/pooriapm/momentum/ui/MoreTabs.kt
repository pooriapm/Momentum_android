package com.pooriapm.momentum.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.HealthAndSafety
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.BuildConfig
import com.pooriapm.momentum.billing.BazaarBilling
import com.pooriapm.momentum.billing.BillingResult
import com.pooriapm.momentum.data.Dashboard
import com.pooriapm.momentum.ui.components.InfoCard

@Composable
fun ProgressTab(dashboard: Dashboard) {
    val series = dashboard.progressSeries
    val weights = dashboard.recentCheckins.filter { it.weightKg != null }.sortedBy { it.localDate }
    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("روندی که ساخته‌ای", style = MaterialTheme.typography.headlineSmall)
            Text("نمره نیست؛ هر هفته با هفته قبلش مقایسه می‌شود.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            InfoCard("پایبندی هفتگی") {
                if (series.isEmpty()) {
                    Text("بعد از چند روز ثبت وعده و تمرین، نمودارت اینجا ساخته می‌شود.")
                } else {
                    WeeklyBars(series.map { it.week to it.adherence }, series.map { it.partial })
                    series.lastOrNull()?.let { last ->
                        Text("این هفته: وعده‌ها ${last.mealsCompleted.fa()} از ${last.mealsPlanned.fa()} · تمرین ${last.workoutsCompleted.fa()} از ${last.workoutsPlanned.fa()}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            InfoCard("وزن") {
                if (weights.isEmpty()) {
                    Text("وزن را در چک‌این روزانه وارد کن تا روندش را ببینی.")
                } else {
                    val first = weights.first().weightKg!!
                    val last = weights.last().weightKg!!
                    Text("${last.toString().fa()} کیلوگرم", style = MaterialTheme.typography.headlineMedium)
                    val diff = last - first
                    Text(
                        when {
                            diff < -0.05 -> "${"%.1f".format(java.util.Locale.ROOT, -diff).fa()} کیلوگرم کمتر از ${jalaliLabel(weights.first().localDate)}"
                            diff > 0.05 -> "${"%.1f".format(java.util.Locale.ROOT, diff).fa()} کیلوگرم بیشتر از ${jalaliLabel(weights.first().localDate)}"
                            else -> "بدون تغییر"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    weights.takeLast(7).reversed().forEach {
                        Row(Modifier.fillMaxWidth()) {
                            Text(jalaliLabel(it.localDate), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("${it.weightKg.toString().fa()} کیلوگرم", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/** Simple bar chart of weekly adherence with an accessible text alternative. */
@Composable
private fun WeeklyBars(points: List<Pair<Int, Int>>, partial: List<Boolean>) {
    val description = points.joinToString("، ") { (week, value) -> "هفته ${week.fa()} ${value.fa()}٪" }
    Row(
        Modifier.fillMaxWidth().height(140.dp).semantics { contentDescription = "پایبندی: $description" },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        points.forEachIndexed { index, (week, value) ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${value.fa()}٪", style = MaterialTheme.typography.labelSmall)
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight((value / 100f).coerceIn(0.04f, 0.8f))
                        .background(
                            if (partial[index]) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f) else MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                        ),
                )
                Text("هفته ${week.fa()}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun MeTab(state: AppState, dashboard: Dashboard, billing: BazaarBilling) {
    val context = LocalContext.current
    fun open(path: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.SITE_URL.trimEnd('/')}/fa$path")))
    var buying by rememberSaveable { mutableStateOf(false) }
    val entitlement = dashboard.entitlementUsage?.entitlement
    val active = entitlement?.status == "active"
    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(dashboard.profile.displayName, style = MaterialTheme.typography.headlineSmall)
            Text(state.api.session?.email.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("  عضویت Momentum", style = MaterialTheme.typography.titleLarge)
                    }
                    Text(
                        when {
                            active && entitlement?.source == "gift" -> "هدیه ماه اول فعال است تا ${jalaliLabel(entitlement.periodEnd)}."
                            active -> "فعال تا ${jalaliLabel(entitlement!!.periodEnd)}."
                            else -> "عضویت فعال نیست. هر ۳۰ روز عضویت، برنامه ماهانه تازه، جایگزینی وعده، فهرست خرید و روند پیشرفت را باز می‌کند."
                        },
                    )
                    Text("۴۹۰٬۰۰۰ تومان برای ۳۰ روز · پرداخت امن کافه‌بازار · بدون تمدید خودکار",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(
                        enabled = !buying,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        onClick = {
                            buying = true
                            billing.buyMonth { result ->
                                buying = false
                                when (result) {
                                    is BillingResult.Granted -> { state.notice = "پرداخت تأیید شد و عضویت ۳۰ روزه فعال شد."; state.refresh() }
                                    BillingResult.Cancelled -> state.notice = "خرید لغو شد و مبلغی کم نشد."
                                    is BillingResult.Failed -> state.error = result.message
                                }
                            }
                        },
                    ) {
                        if (buying) CircularProgressIndicator(strokeWidth = 2.dp)
                        else Text(if (active) "تمدید ۳۰ روز" else "خرید عضویت")
                    }
                    if (active) Text("پرداخت زودتر، ۳۰ روز به پایان عضویت فعلی اضافه می‌کند.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { OutlinedCardRow(Icons.Outlined.HealthAndSafety, "مرز ایمنی", "Momentum تشخیص و درمان پزشکی نیست.") { open("/safety") } }
        item { OutlinedCardRow(Icons.Outlined.PrivacyTip, "حریم خصوصی", "چه داده‌ای نگه می‌داریم و چطور حذف می‌شود.") { open("/privacy") } }
        item { OutlinedCardRow(Icons.Outlined.Gavel, "شرایط استفاده", "قوانین استفاده از Momentum") { open("/terms") } }
        item { OutlinedCardRow(Icons.Outlined.Language, "خروجی یا حذف حساب", "در نسخه وب انجام می‌شود.") { open("/app/account") } }
        item { OutlinedCardRow(Icons.AutoMirrored.Outlined.Logout, "خروج از حساب", "داده‌ها در حساب می‌مانند.") { state.signOut() } }
        item { Text("نسخه ${BuildConfig.VERSION_NAME.fa()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun NoPlanTab(state: AppState, dashboard: Dashboard) {
    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("سلام، ${dashboard.profile.displayName}", style = MaterialTheme.typography.headlineMedium)
        }
        item {
            InfoCard(if (dashboard.profile.onboardingStatus == "automation_blocked") "برنامه خودکار برایت مناسب نیست" else "برنامه این ماهت هنوز ساخته نشده") {
                if (dashboard.profile.onboardingStatus == "automation_blocked") {
                    Text("بر اساس پاسخ‌های ایمنی، ساخت خودکار برنامه متوقف است. لطفاً با یک متخصص سلامت مشورت کن.")
                } else {
                    Text("راه‌اندازی کامل است. با یک دکمه، برنامه ۳۰ روزه غذا و تمرینت ساخته می‌شود.")
                    Button(onClick = state::generatePlan, enabled = !state.generating, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        if (state.generating) CircularProgressIndicator(strokeWidth = 2.dp) else Text("ساخت برنامه")
                    }
                    if (state.generating) Text("معمولاً یکی دو دقیقه طول می‌کشد. می‌توانی برنامه را ببندی؛ کار ادامه دارد.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
