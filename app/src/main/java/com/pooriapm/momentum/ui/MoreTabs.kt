package com.pooriapm.momentum.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.pooriapm.momentum.data.Entitlement
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
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
                    Text("وزن را در گزارش روزانه وارد کن تا روندش را ببینی.")
                } else {
                    val first = weights.first().weightKg!!
                    val last = weights.last().weightKg!!
                    Text("${last.faDecimal()} کیلوگرم", style = MaterialTheme.typography.headlineMedium)
                    val diff = last - first
                    Text(
                        when {
                            diff < -0.05 -> "${(-diff).faDecimal()} کیلوگرم کمتر از ${jalaliLabel(weights.first().localDate)}"
                            diff > 0.05 -> "${diff.faDecimal()} کیلوگرم بیشتر از ${jalaliLabel(weights.first().localDate)}"
                            else -> "بدون تغییر"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    weights.takeLast(7).reversed().forEach {
                        Row(Modifier.fillMaxWidth()) {
                            Text(jalaliLabel(it.localDate), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text("${it.weightKg?.faDecimal()} کیلوگرم", style = MaterialTheme.typography.bodyMedium)
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
    var page by rememberSaveable { mutableStateOf<Pair<String, String>?>(null) }
    fun open(path: String, title: String) { page = "${BuildConfig.SITE_URL.trimEnd('/')}/fa$path" to title }
    var buying by rememberSaveable { mutableStateOf(false) }
    fun buy() {
        if (!state.online) {
            state.message = UiMessage(com.pooriapm.momentum.data.persianError("offline", com.pooriapm.momentum.data.ErrorKind.Offline))
            return
        }
        buying = true
        billing.buyMonth { result ->
            buying = false
            state.message = when (result) {
                is BillingResult.Granted -> UiMessage("پرداخت تأیید شد و ۳۰ روز به عضویتت اضافه شد.", isError = false).also { state.refresh() }
                BillingResult.Cancelled -> UiMessage("خرید لغو شد و مبلغی کم نشد.", isError = false)
                is BillingResult.Failed -> UiMessage(result.message)
            }
        }
    }

    LazyColumn(contentPadding = screenPadding, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(dashboard.profile.displayName, style = MaterialTheme.typography.headlineSmall)
                Text(state.api.session?.email.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { MembershipCard(dashboard.entitlementUsage?.entitlement, buying, ::buy) }
        item {
            SettingsGroup {
                SettingsRow(Icons.Outlined.HealthAndSafety, "مرز ایمنی") { open("/safety", "مرز ایمنی") }
                SettingsRow(Icons.Outlined.PrivacyTip, "حریم خصوصی") { open("/privacy", "حریم خصوصی") }
                SettingsRow(Icons.Outlined.Gavel, "شرایط استفاده") { open("/terms", "شرایط استفاده") }
                SettingsRow(Icons.Outlined.Download, "دریافت یا حذف داده‌ها", last = true) { open("/app/account", "داده‌های حساب") }
            }
        }
        item {
            SettingsGroup {
                SettingsRow(Icons.AutoMirrored.Outlined.Logout, "خروج از حساب", tint = MaterialTheme.colorScheme.error, chevron = false, last = true) { state.signOut() }
            }
        }
        item {
            Text(
                "Momentum · نسخه ${BuildConfig.VERSION_NAME.fa()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    page?.let { (url, title) -> InAppPage(url, title) { page = null } }
}

/** Where a membership stands right now. */
private data class MembershipStatus(val active: Boolean, val daysLeft: Int, val totalDays: Int, val gift: Boolean)

private fun membershipStatus(entitlement: Entitlement?): MembershipStatus {
    val end = entitlement?.periodEnd?.let(::parseInstant)
    val now = Instant.now()
    if (entitlement == null || end == null || entitlement.status !in setOf("active", "grace") || !end.isAfter(now)) {
        return MembershipStatus(false, 0, 30, false)
    }
    val day = 86_400L
    val left = ((Duration.between(now, end).seconds + day - 1) / day).toInt()
    val total = entitlement.periodStart?.let(::parseInstant)
        ?.let { ((Duration.between(it, end).seconds + day - 1) / day).toInt() }
        ?.coerceAtLeast(left) ?: maxOf(30, left)
    return MembershipStatus(true, left, total, entitlement.source == "gift")
}

private fun parseInstant(iso: String): Instant? =
    runCatching { OffsetDateTime.parse(iso).toInstant() }.getOrNull()
        ?: runCatching { LocalDate.parse(iso.take(10)).atStartOfDay(ZoneId.systemDefault()).toInstant() }.getOrNull()

/**
 * Membership at a glance. Active: days left (large), how much of the period remains, the end date
 * and when to renew; in the last 5 days it becomes a renewal reminder. Otherwise: the offer.
 */
@Composable
private fun MembershipCard(entitlement: Entitlement?, buying: Boolean, onBuy: () -> Unit) {
    val status = membershipStatus(entitlement)
    val colors = MaterialTheme.colorScheme
    val endingSoon = status.active && status.daysLeft <= 5
    Card(
        colors = CardDefaults.cardColors(containerColor = if (endingSoon) colors.secondaryContainer else colors.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = colors.primary)
                Text("عضویت Momentum", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                if (status.active) {
                    Text(
                        if (status.gift) "هدیه" else "فعال",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onTertiary,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.tertiary).padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
            }
            if (status.active) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        status.daysLeft.fa(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (endingSoon) colors.secondary else colors.onPrimaryContainer,
                    )
                    Text(" روز مانده", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 6.dp))
                }
                LinearProgressIndicator(
                    progress = { (status.daysLeft.toFloat() / status.totalDays).coerceIn(0f, 1f) },
                    color = if (endingSoon) colors.secondary else colors.primary,
                    trackColor = colors.surface.copy(alpha = 0.6f),
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                )
                val end = parseInstant(entitlement!!.periodEnd)!!
                val renewFrom = jalaliLabel(end.minus(Duration.ofDays(5)).atZone(ZoneId.systemDefault()).toLocalDate().toString())
                Text(
                    "پایان: ${jalaliLabel(entitlement.periodEnd)}" +
                        if (endingSoon) " · برای اینکه برنامه‌ات قطع نشود همین حالا تمدید کن." else " · زمان تمدید: از $renewFrom",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                if (endingSoon) {
                    Button(onClick = onBuy, enabled = !buying, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { BuyLabel(buying, "تمدید ۳۰ روز") }
                } else {
                    OutlinedButton(onClick = onBuy, enabled = !buying, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { BuyLabel(buying, "تمدید زودتر") }
                }
                Text(
                    "پرداخت زودتر چیزی را هدر نمی‌دهد: ۳۰ روز به انتهای عضویت فعلی اضافه می‌شود.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            } else {
                Text("برنامه ماهانه تازه، جایگزینی وعده، فهرست خرید و روند پیشرفت.", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "۴۹۰٬۰۰۰ تومان برای ۳۰ روز · کافه‌بازار · بدون تمدید خودکار",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Button(onClick = onBuy, enabled = !buying, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { BuyLabel(buying, "خرید عضویت") }
            }
        }
    }
}

@Composable
private fun BuyLabel(buying: Boolean, label: String) {
    if (buying) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) else Text(label)
}

/** iOS-style grouped list: one rounded card with thin dividers between rows. */
@Composable
private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) { Column(content = content) }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    tint: Color = MaterialTheme.colorScheme.primary,
    chevron: Boolean = true,
    last: Boolean = false,
    onClick: () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (chevron) Color.Unspecified else tint,
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            )
            if (chevron) {
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (!last) HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
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
