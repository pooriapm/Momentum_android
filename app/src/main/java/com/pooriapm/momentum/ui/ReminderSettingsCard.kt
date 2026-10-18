package com.pooriapm.momentum.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.reminders.ReminderSettings
import com.pooriapm.momentum.reminders.Reminders

/** Reminder switches on the Me tab; turning one on asks for notification permission when needed. */
@Composable
fun ReminderSettingsCard(state: AppState) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(Reminders.settings(context)) }
    var allowed by remember { mutableStateOf(Reminders.canNotify(context)) }
    var timeOpen by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<ReminderSettings?>(null) }

    fun apply(next: ReminderSettings) {
        settings = next
        Reminders.saveSettings(context, next)
        Reminders.sync(context, state.dashboard, state.workout?.status == "completed")
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        allowed = granted
        pending?.let { if (granted) apply(it) }
        pending = null
    }
    fun change(next: ReminderSettings) {
        val turningOn = (next.meals && !settings.meals) || (next.workout && !settings.workout)
        if (turningOn && !allowed && Build.VERSION.SDK_INT >= 33) {
            pending = next
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else apply(next)
    }

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.NotificationsActive, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Text("یادآوری‌ها", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 14.dp))
            }
            if (!allowed && (settings.meals || settings.workout)) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("اعلان‌ها برای Momentum خاموش است.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else runCatching {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }
                    }) { Text("روشن کردن") }
                }
            }
            ToggleRow(Icons.Outlined.Restaurant, "وقت وعده‌ها", "سر ساعت هر وعده؛ وعده‌های ثبت‌شده یادآوری ندارند.", settings.meals && allowed) {
                change(settings.copy(meals = it))
            }
            HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
            ToggleRow(Icons.Outlined.FitnessCenter, "تمرین", "روزهای تمرین، ساعت ${clock(settings.workoutMinutes)}", settings.workout && allowed) {
                change(settings.copy(workout = it))
            }
            if (settings.workout) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { timeOpen = true }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Text("ساعت یادآوری تمرین", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
                    Text(clock(settings.workoutMinutes), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (timeOpen) TimeSheet(settings.workoutMinutes, onDismiss = { timeOpen = false }) {
        timeOpen = false
        apply(settings.copy(workoutMinutes = it))
    }
}

private fun clock(minutes: Int) = "%02d:%02d".format(java.util.Locale.ROOT, minutes / 60, minutes % 60).fa()

@Composable
private fun ToggleRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(role = Role.Switch) { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** Hour picker in 30-minute steps from 05:00 to 23:00 — enough for a reminder. */
@Composable
private fun TimeSheet(current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val options = (5 * 60..23 * 60 step 30).toList()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ساعت یادآوری تمرین") },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                items(options.size) { i ->
                    val m = options[i]
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { onPick(m) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(clock(m), style = MaterialTheme.typography.titleMedium,
                            color = if (m == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}
