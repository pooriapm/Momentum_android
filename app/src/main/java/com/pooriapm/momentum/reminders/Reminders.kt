package com.pooriapm.momentum.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pooriapm.momentum.MainActivity
import com.pooriapm.momentum.R
import com.pooriapm.momentum.data.Dashboard
import com.pooriapm.momentum.ui.fa
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** What to remind and when; saved so reminders can be rebuilt after a reboot without the network. */
@Serializable
data class Reminder(val id: Int, val atMillis: Long, val title: String, val text: String)

@Serializable
data class ReminderSettings(
    val meals: Boolean = true,
    val workout: Boolean = true,
    /** Minutes after midnight; the plan has no workout time, so the user picks one. */
    val workoutMinutes: Int = 18 * 60,
)

/**
 * Local reminders for meal times and the workout. They are computed from the cached plan for today
 * and tomorrow, skip what is already eaten, skipped or done, and are rebuilt whenever the app opens
 * or the plan changes and after a reboot. Inexact windows: no exact-alarm permission is needed, and
 * a reminder a few minutes late is fine.
 */
object Reminders {
    const val CHANNEL = "momentum.reminders"
    private const val PREFS = "momentum.reminders"
    private const val WINDOW_MS = 5 * 60_000L
    private val json = Json { ignoreUnknownKeys = true }

    fun settings(context: Context): ReminderSettings =
        prefs(context).getString("settings", null)?.let { runCatching { json.decodeFromString<ReminderSettings>(it) }.getOrNull() }
            ?: ReminderSettings()

    fun saveSettings(context: Context, settings: ReminderSettings) {
        prefs(context).edit().putString("settings", json.encodeToString(ReminderSettings.serializer(), settings)).apply()
    }

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Rebuilds all reminders from the dashboard (call on every successful load and on settings change). */
    fun sync(context: Context, dashboard: Dashboard?, workoutDone: Boolean) {
        val wanted = dashboard?.let { plan(it, settings(context), workoutDone) }.orEmpty()
        replace(context, wanted)
    }

    /** Removes every reminder (sign-out). */
    fun clear(context: Context) = replace(context, emptyList())

    /** After a reboot alarms are gone: schedule the saved ones again (past ones are dropped). */
    fun restore(context: Context) {
        val saved = saved(context).filter { it.atMillis > System.currentTimeMillis() }
        replace(context, saved)
    }

    internal fun plan(dashboard: Dashboard, settings: ReminderSettings, workoutDone: Boolean): List<Reminder> {
        val plan = dashboard.plan ?: return emptyList()
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val today = runCatching { LocalDate.parse(dashboard.localDate) }.getOrNull() ?: return emptyList()
        val days = (plan.days.ifEmpty { listOf(plan.day) })
            .filter { it.localDate == today.toString() || it.localDate == today.plusDays(1).toString() }
        val out = mutableListOf<Reminder>()
        days.forEachIndexed { dayIndex, day ->
            val date = LocalDate.parse(day.localDate)
            if (settings.meals) {
                day.meals.forEachIndexed { i, meal ->
                    // Server state for today; tomorrow is always still planned.
                    if (meal.completionStatus != "planned") return@forEachIndexed
                    val time = meal.scheduledTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: return@forEachIndexed
                    val at = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    if (at <= now) return@forEachIndexed
                    val option = meal.chosenOption?.title.orEmpty()
                    out += Reminder(
                        id = 1000 + dayIndex * 100 + i,
                        atMillis = at,
                        title = "وقت ${meal.title}",
                        text = if (option.isBlank()) "وعده‌ات را بخور و در Momentum ثبتش کن." else "$option · بعد از خوردن، ثبتش کن.",
                    )
                }
            }
            val workout = day.workout
            if (settings.workout && workout != null && !(day.localDate == today.toString() && workoutDone)) {
                val at = date.atStartOfDay(zone).plusMinutes(settings.workoutMinutes.toLong()).toInstant().toEpochMilli()
                if (at > now) {
                    out += Reminder(
                        id = 2000 + dayIndex,
                        atMillis = at,
                        title = "وقت تمرین",
                        text = "${workout.title} · ${workout.durationMinutes.fa()} دقیقه",
                    )
                }
            }
        }
        return out
    }

    private fun replace(context: Context, reminders: List<Reminder>) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        saved(context).forEach { alarms.cancel(pending(context, it)) }
        reminders.forEach { alarms.setWindow(AlarmManager.RTC_WAKEUP, it.atMillis, WINDOW_MS, pending(context, it)) }
        prefs(context).edit()
            .putString("scheduled", json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Reminder.serializer()), reminders))
            .apply()
    }

    private fun saved(context: Context): List<Reminder> =
        prefs(context).getString("scheduled", null)?.let {
            runCatching { json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Reminder.serializer()), it) }.getOrNull()
        }.orEmpty()

    private fun pending(context: Context, reminder: Reminder): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            reminder.id,
            Intent(context, ReminderReceiver::class.java)
                .putExtra("id", reminder.id)
                .putExtra("title", reminder.title)
                .putExtra("text", reminder.text),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "یادآوری وعده‌ها و تمرین", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "یادآوری زمان وعده‌ها و تمرین برنامه‌ات"
            },
        )
    }

    fun show(context: Context, id: Int, title: String, text: String) {
        if (!canNotify(context)) {
            android.util.Log.w("MomentumReminders", "notifications not allowed")
            return
        }
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setColor(0xFFA35E00.toInt())
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
            .onFailure { android.util.Log.w("MomentumReminders", "notify failed: ${it::class.java.simpleName}") }
            .onFailure { android.util.Log.w("MomentumReminders", "notify failed: ${it::class.java.simpleName}") }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.show(
            context,
            intent.getIntExtra("id", 0),
            intent.getStringExtra("title").orEmpty(),
            intent.getStringExtra("text").orEmpty(),
        )
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Reminders.restore(context)
        }
    }
}
