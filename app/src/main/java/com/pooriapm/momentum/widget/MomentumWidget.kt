package com.pooriapm.momentum.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.currentState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.action.actionStartActivity
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pooriapm.momentum.MainActivity
import com.pooriapm.momentum.data.Api
import com.pooriapm.momentum.data.Dashboard
import com.pooriapm.momentum.ui.fa
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class WidgetMeal(
    val slotKey: String,
    val title: String,
    val option: String,
    val optionKey: String,
    val time: String? = null,
    val status: String,
)

/** What the widget shows, saved by the app after each dashboard load (no network needed to draw). */
@Serializable
data class WidgetSnapshot(val localDate: String, val meals: List<WidgetMeal>)

/** The launcher lays the widget out in the phone's language; this mark keeps Persian lines right-to-left. */
private fun rtl(text: String) = "‏$text"

private val Cream = Color(0xFFFBF6EE)
private val Ink = Color(0xFF1C1A17)
private val Muted = Color(0xFF6E675D)
private val Saffron = Color(0xFFA35E00)
private val Green = Color(0xFF2B7A4B)

/**
 * Home-screen widget: today's next meal and a one-tap "I ate it". Tapping elsewhere opens the app.
 * The tick calls the same API as the app; the widget then moves on to the next meal.
 */
class MomentumWidget : GlanceAppWidget() {
    // Glance keeps per-widget state and redraws when it changes; the snapshot JSON lives there.
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val json = currentState<Preferences>()[SnapshotKey] ?: load(context)?.let { encode(it) }
            GlanceTheme { Content(json?.let(::decode)) }
        }
    }

    @Composable
    private fun Content(snapshot: WidgetSnapshot?) {
        val today = java.time.LocalDate.now().toString()
        val current = snapshot?.takeIf { it.localDate == today }
        val next = current?.meals?.firstOrNull { it.status == "planned" }
        val done = current?.meals?.count { it.status == "completed" } ?: 0
        val total = current?.meals?.size ?: 0
        Box(
            GlanceModifier.fillMaxSize().background(ColorProvider(Cream)).cornerRadius(24.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Column(GlanceModifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically) {
                Text("Momentum", style = TextStyle(color = ColorProvider(Saffron), fontWeight = FontWeight.Bold, fontSize = 13.sp))
                Spacer(GlanceModifier.height(6.dp))
                when {
                    current == null -> Text(
                        rtl("برای دیدن برنامه امروز، اپ را باز کن."),
                        style = TextStyle(color = ColorProvider(Muted), fontSize = 13.sp, textAlign = TextAlign.Center),
                    )
                    next == null -> Text(
                        rtl("همه وعده‌های امروز ثبت شد 🎉"),
                        style = TextStyle(color = ColorProvider(Green), fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                    )
                    else -> {
                        Text(
                            rtl("${next.title}${next.time?.let { " · ${it.fa()}" } ?: ""}"),
                            style = TextStyle(color = ColorProvider(Muted), fontSize = 12.sp, textAlign = TextAlign.Center),
                        )
                        Text(
                            rtl(next.option),
                            maxLines = 2,
                            style = TextStyle(color = ColorProvider(Ink), fontSize = 16.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                        )
                        Spacer(GlanceModifier.height(8.dp))
                        Box(
                            GlanceModifier.fillMaxWidth().background(ColorProvider(Saffron)).cornerRadius(20.dp).padding(vertical = 8.dp)
                                .clickable(actionRunCallback<MarkEatenAction>()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(rtl("خوردم ✓"), style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                }
                if (total > 0) {
                    Spacer(GlanceModifier.height(6.dp))
                    Text(rtl("${done.fa()} از ${total.fa()} وعده"), style = TextStyle(color = ColorProvider(Muted), fontSize = 11.sp))
                }
            }
        }
    }

    companion object {
        private const val PREFS = "momentum.widget"
        private val json = Json { ignoreUnknownKeys = true }
        private val SnapshotKey = stringPreferencesKey("snapshot")

        private fun encode(snapshot: WidgetSnapshot) = json.encodeToString(WidgetSnapshot.serializer(), snapshot)
        private fun decode(text: String) = runCatching { json.decodeFromString(WidgetSnapshot.serializer(), text) }.getOrNull()

        /** Pushes [snapshot] into every placed widget's state, which redraws them. */
        private suspend fun push(context: Context, snapshot: WidgetSnapshot?) {
            val manager = GlanceAppWidgetManager(context)
            manager.getGlanceIds(MomentumWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { prefs ->
                    if (snapshot == null) prefs.remove(SnapshotKey) else prefs[SnapshotKey] = encode(snapshot)
                }
                MomentumWidget().update(context, id)
            }
        }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        fun load(context: Context): WidgetSnapshot? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("snapshot", null)
                ?.let { runCatching { json.decodeFromString(WidgetSnapshot.serializer(), it) }.getOrNull() }

        private fun save(context: Context, snapshot: WidgetSnapshot?) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                if (snapshot == null) remove("snapshot") else putString("snapshot", json.encodeToString(WidgetSnapshot.serializer(), snapshot))
            }.apply()
        }

        /** Called by the app whenever the dashboard changes (null on sign-out), then redraws widgets. */
        fun saveSnapshot(context: Context, dashboard: Dashboard?) {
            val day = dashboard?.plan?.day
            val snapshot = if (dashboard == null || day == null) null else WidgetSnapshot(
                localDate = dashboard.localDate,
                meals = day.meals.mapNotNull { meal ->
                    val option = meal.chosenOption ?: return@mapNotNull null
                    WidgetMeal(meal.slotKey, meal.title, option.title, option.optionKey, meal.scheduledTime, meal.completionStatus)
                },
            )
            save(context, snapshot)
            scope.launch { runCatching { push(context, snapshot) } }
        }

        /** Marks [meal] eaten in the saved snapshot after the server accepted it. */
        internal suspend fun markLocal(context: Context, slotKey: String) {
            val snapshot = load(context) ?: return
            val next = snapshot.copy(meals = snapshot.meals.map { if (it.slotKey == slotKey) it.copy(status = "completed") else it })
            save(context, next)
            push(context, next)
        }
    }
}

/** The widget's "I ate it": records the next meal through the API, then redraws. */
class MarkEatenAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val snapshot = MomentumWidget.load(context) ?: return
        val meal = snapshot.meals.firstOrNull { it.status == "planned" } ?: return
        val ok = runCatching { Api(context.applicationContext).completeMeal(snapshot.localDate, meal.slotKey, meal.optionKey) }.isSuccess
        if (ok) {
            MomentumWidget.markLocal(context, meal.slotKey)
            // Tell the app its cached day is stale, so it reloads when opened.
            context.getSharedPreferences("momentum.local", Context.MODE_PRIVATE).edit()
                .putLong("widget_changed_at", System.currentTimeMillis()).apply()
        } else {
            // Signed out, offline or the day changed: open the app, which shows the real reason.
            context.startActivity(android.content.Intent(context, MainActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

class MomentumWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MomentumWidget()
}
