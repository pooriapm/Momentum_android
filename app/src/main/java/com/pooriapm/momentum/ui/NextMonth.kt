package com.pooriapm.momentum.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.data.Dashboard
import com.pooriapm.momentum.data.PlanHistoryItem
import com.pooriapm.momentum.data.asApi
import com.pooriapm.momentum.ui.components.MomentumField
import kotlinx.coroutines.launch
import java.time.LocalDate

/** A plan already built for next month (created in the current plan's last days), if any. */
fun upcomingPlan(dashboard: Dashboard): PlanHistoryItem? =
    dashboard.planHistory.filter { it.validFrom > dashboard.localDate }.minByOrNull { it.validFrom }

/** Days left in the current plan including today, or null without a plan. */
fun daysLeft(dashboard: Dashboard): Int? {
    val plan = dashboard.plan ?: return null
    val today = runCatching { LocalDate.parse(dashboard.localDate) }.getOrNull() ?: return null
    val end = runCatching { LocalDate.parse(plan.validTo) }.getOrNull() ?: return null
    return (end.toEpochDay() - today.toEpochDay() + 1).toInt()
}

/**
 * Shown on Today in a plan's last 3 days: when it ends, that the next month is built from the
 * current preferences, and a note the user can leave for it.
 */
@Composable
fun PlanEndingCard(state: AppState, dashboard: Dashboard, onEditPreferences: () -> Unit) {
    val left = daysLeft(dashboard) ?: return
    if (left > 3) return
    var noteOpen by rememberSaveable { mutableStateOf(false) }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.EventRepeat, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(10.dp))
                Text(
                    if (left <= 1) "امروز آخرین روز برنامه این ماه است" else "${left.fa()} روز تا پایان برنامه این ماه",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                "برنامه ماه بعد بر اساس ترجیحات و گزارش‌های روزانه‌ات ساخته می‌شود. اگر چیزی باید عوض شود، الان بگو.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            upcomingPlan(dashboard)?.let {
                Text("برنامه ماه بعد آماده است و از ${jalaliLabel(it.validFrom)} شروع می‌شود.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { noteOpen = true }) {
                    Icon(Icons.Outlined.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("یادداشت برای ماه بعد")
                }
                TextButton(onClick = onEditPreferences) { Text("ترجیحات") }
            }
        }
    }
    if (noteOpen) NextCycleNoteSheet(state) { noteOpen = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextCycleNoteSheet(state: AppState, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            val loaded = state.api.nextCycleNote()
            available = loaded != null
            note = loaded.orEmpty()
        } catch (raw: Exception) {
            note = ""
            state.message = UiMessage(raw.asApi().message.orEmpty())
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("یادداشت برای ماه بعد", style = MaterialTheme.typography.headlineSmall)
            Text(
                "مثلاً «شام‌ها سبک‌تر باشد» یا «جمعه‌ها تمرین نمی‌کنم». موقع ساخت برنامه بعدی در نظر گرفته می‌شود.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val current = note
            when {
                current == null -> Box(Modifier.fillMaxWidth().heightIn(min = 120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                !available -> Text("یادداشت ماه بعد هنوز برای برنامه تو فعال نیست. می‌توانی از «ترجیحات برنامه» تغییرهایت را ثبت کنی.",
                    style = MaterialTheme.typography.bodyMedium)
                else -> {
                    MomentumField(
                        value = current,
                        onValueChange = { if (it.length <= 500) note = it },
                        label = "یادداشت",
                        placeholder = "چه چیزی باید فرق کند؟",
                        singleLine = false,
                        minLines = 3,
                        supporting = "${current.length.fa()} از ۵۰۰",
                    )
                    Button(
                        onClick = {
                            saving = true
                            scope.launch {
                                try {
                                    state.api.saveNextCycleNote(current.trim())
                                    state.message = UiMessage("یادداشت ماه بعد ذخیره شد.", isError = false)
                                    onDismiss()
                                } catch (raw: Exception) {
                                    state.message = UiMessage(raw.asApi().message.orEmpty())
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    ) {
                        if (saving) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) else Text("ذخیره")
                    }
                }
            }
        }
    }
}

/** Past monthly plans, newest first; the current one is marked. Shown only once there are two. */
@Composable
fun PlanHistoryCard(history: List<PlanHistoryItem>) {
    if (history.size < 2) return
    var open by rememberSaveable { mutableStateOf(false) }
    val sorted = history.sortedByDescending { it.validFrom }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("برنامه‌های قبلی", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                Text("${sorted.size.fa()} ماه", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (open) {
                sorted.forEachIndexed { i, item ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(
                                if (item.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                (sorted.size - i).fa(),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (item.active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text("ماه ${(sorted.size - i).fa()}${if (item.active) " · فعلی" else ""}", style = MaterialTheme.typography.bodyLarge)
                            Text("${jalaliLabel(item.validFrom)} تا ${jalaliLabel(item.validTo)}", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            when (item.source) {
                                "deterministic" -> "برنامه پایه"
                                "imported" -> "واردشده"
                                else -> "هوشمند"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
