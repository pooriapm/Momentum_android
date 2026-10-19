package com.pooriapm.momentum.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pooriapm.momentum.MainActivity
import com.pooriapm.momentum.data.Api
import com.pooriapm.momentum.data.Dashboard
import com.pooriapm.momentum.ui.fa
import com.pooriapm.momentum.ui.faDecimal
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
data class WidgetSnapshot(
    val localDate: String,
    val meals: List<WidgetMeal>,
    val waterTargetMl: Int = 2200,
    val timezone: String? = null,
)

/** The launcher lays the widget out in the phone's language; this mark keeps Persian lines right-to-left. */
private fun rtl(text: String) = "‏$text"

private val Cream = Color(0xFFFBF6EE)
private val Ink = Color(0xFF1C1A17)
private val Muted = Color(0xFF6E675D)
private val Saffron = Color(0xFFA35E00)
private val Green = Color(0xFF2B7A4B)
private val Water = Color(0xFF3DA5F4)
private val WaterSoft = Color(0xFFE3F2FD)
private val WaterEmpty = Color(0xFFC6E2F7)
private val SaffronSoft = Color(0xFFF6E7CF)

private val SMALL = DpSize(110.dp, 40.dp)
private val COMPACT = DpSize(170.dp, 100.dp)
private val MEDIUM = DpSize(170.dp, 170.dp)
private val LARGE = DpSize(170.dp, 300.dp)

private fun litres(ml: Int) = (ml / 1000.0).faDecimal()

/**
 * Compact home-screen widget: the next meal with a one-tap tick, and today's water with a one-tap
 * glass. While a tap is being saved its button shows a spinner, so it is clear the tap landed.
 */
class MomentumWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val prefs = currentState<Preferences>()
            val snapshot = (prefs[SnapshotKey] ?: load(context)?.let { encode(it) })?.let(::decode)
            val water = prefs[WaterKey]?.toIntOrNull() ?: snapshot?.let { waterOf(context, it.localDate) } ?: 0
            GlanceTheme { Content(snapshot, water, prefs[BusyKey]) }
        }
    }

    // One design per size, so the widget never looks empty: a strip, two rows, a card, a full day.
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, COMPACT, MEDIUM, LARGE))

    @Composable
    private fun Content(snapshot: WidgetSnapshot?, waterMl: Int, busy: String?) {
        val today = java.time.LocalDate.now().toString()
        val current = snapshot?.takeIf { it.localDate == today }
        val size = LocalSize.current
        Box(
            GlanceModifier.fillMaxSize().background(ColorProvider(Cream)).cornerRadius(22.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clickable(actionStartActivity<MainActivity>()),
            contentAlignment = Alignment.Center,
        ) {
            when {
                current == null -> Text(
                    rtl("Momentum · برای دیدن برنامه امروز اپ را باز کن"),
                    style = TextStyle(color = ColorProvider(Muted), fontSize = 12.sp, textAlign = TextAlign.Center),
                )
                size.height >= LARGE.height -> Large(current, waterMl, busy)
                size.height >= MEDIUM.height -> Medium(current, waterMl, busy)
                size.height >= COMPACT.height -> Compact(current, waterMl, busy)
                else -> Small(current, waterMl, busy)
            }
        }
    }

    /** One row: next meal and its tick, water as a short line. */
    @Composable
    private fun Small(day: WidgetSnapshot, waterMl: Int, busy: String?) {
        val next = day.meals.firstOrNull { it.status == "planned" }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    rtl(next?.option ?: "همه وعده‌ها ثبت شد 🎉"),
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(if (next == null) Green else Ink), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                )
                Text(
                    rtl("${next?.title?.let { "$it · " } ?: ""}💧 ${litres(waterMl)} از ${litres(day.waterTargetMl)}"),
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(Muted), fontSize = 11.sp),
                )
            }
            Spacer(GlanceModifier.width(6.dp))
            ActionButton("+", busy == BUSY_WATER, Water, busy == null, actionRunCallback<AddWaterAction>(), small = true)
            if (next != null) {
                Spacer(GlanceModifier.width(6.dp))
                ActionButton("✓", busy == BUSY_MEAL, Saffron, busy == null, actionRunCallback<MarkEatenAction>())
            }
        }
    }

    /** Two rows for a short, wide widget: the next meal with its tick, then water. */
    @Composable
    private fun Compact(day: WidgetSnapshot, waterMl: Int, busy: String?) {
        Column(GlanceModifier.fillMaxSize()) {
            NextMeal(day, busy, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.height(6.dp))
            WaterRow(day, waterMl, busy)
        }
    }

    /** A card: header with progress, next meal with its tick, the day as pills, water glasses with "+". */
    @Composable
    private fun Medium(day: WidgetSnapshot, waterMl: Int, busy: String?) {
        Column(GlanceModifier.fillMaxSize()) {
            Header(day)
            Spacer(GlanceModifier.height(6.dp))
            NextMeal(day, busy, GlanceModifier.defaultWeight())
            Spacer(GlanceModifier.height(4.dp))
            DayStrip(day)
            Spacer(GlanceModifier.height(6.dp))
            WaterRow(day, waterMl, busy)
        }
    }

    /** The whole day: header, every meal with its status, water glasses. */
    @Composable
    private fun Large(day: WidgetSnapshot, waterMl: Int, busy: String?) {
        val nextKey = day.meals.firstOrNull { it.status == "planned" }?.slotKey
        Column(GlanceModifier.fillMaxSize()) {
            Header(day)
            Spacer(GlanceModifier.height(8.dp))
            Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                day.meals.forEach { meal ->
                    val isNext = meal.slotKey == nextKey
                    val row = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
                    // Each meal takes an equal share of the height, so a tall widget spreads out instead of leaving a gap.
                    Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.CenterStart) {
                    Row(
                        (if (isNext) row.background(ColorProvider(SaffronSoft)).cornerRadius(12.dp) else row)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            when (meal.status) {
                                "completed" -> "✅"
                                "skipped" -> "⏭️"
                                else -> "🍽️"
                            },
                            style = TextStyle(fontSize = 13.sp),
                        )
                        Spacer(GlanceModifier.width(6.dp))
                        Column(GlanceModifier.defaultWeight()) {
                            Text(
                                rtl(meal.option),
                                maxLines = 1,
                                style = TextStyle(
                                    color = ColorProvider(if (meal.status == "planned") Ink else Muted),
                                    fontSize = 13.sp,
                                    fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                                ),
                            )
                            Text(
                                rtl("${meal.title}${meal.time?.let { " · ${it.fa()}" } ?: ""}"),
                                maxLines = 1,
                                style = TextStyle(color = ColorProvider(Muted), fontSize = 10.sp),
                            )
                        }
                        if (isNext) ActionButton("✓", busy == BUSY_MEAL, Saffron, busy == null, actionRunCallback<MarkEatenAction>(), small = true)
                    }
                    }
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            WaterRow(day, waterMl, busy)
        }
    }

    /** The day's meals as a strip of pills (green eaten, grey skipped, saffron next), and what comes after. */
    @Composable
    private fun DayStrip(day: WidgetSnapshot) {
        val planned = day.meals.filter { it.status == "planned" }
        val after = planned.getOrNull(1)
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row {
                day.meals.take(8).forEach { meal ->
                    val color = when {
                        meal.status == "completed" -> Green
                        meal.status == "skipped" -> Muted
                        meal.slotKey == planned.firstOrNull()?.slotKey -> Saffron
                        else -> SaffronSoft
                    }
                    Box(GlanceModifier.padding(end = 4.dp)) {
                        Box(GlanceModifier.size(width = 18.dp, height = 6.dp).cornerRadius(3.dp).background(ColorProvider(color))) {}
                    }
                }
            }
            Spacer(GlanceModifier.defaultWeight())
            if (after != null) {
                Text(
                    rtl("بعد: ${after.title}${after.time?.let { " ${it.fa()}" } ?: ""}"),
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(Muted), fontSize = 10.sp),
                )
            }
        }
    }

    @Composable
    private fun Header(day: WidgetSnapshot) {
        val done = day.meals.count { it.status == "completed" }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Momentum", style = TextStyle(color = ColorProvider(Saffron), fontSize = 13.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            Text(rtl("${done.fa()} از ${day.meals.size.fa()} وعده"), style = TextStyle(color = ColorProvider(Muted), fontSize = 11.sp))
        }
    }

    @Composable
    private fun NextMeal(day: WidgetSnapshot, busy: String?, modifier: GlanceModifier) {
        val next = day.meals.firstOrNull { it.status == "planned" }
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight()) {
                if (next == null) {
                    Text(
                        rtl("همه وعده‌های امروز ثبت شد 🎉"),
                        maxLines = 2,
                        style = TextStyle(color = ColorProvider(Green), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                    )
                } else {
                    Text(
                        rtl("${next.title}${next.time?.let { " · ${it.fa()}" } ?: ""}"),
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(Muted), fontSize = 11.sp),
                    )
                    Text(
                        rtl(next.option),
                        maxLines = 1,
                        style = TextStyle(color = ColorProvider(Ink), fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    )
                }
            }
            if (next != null) {
                Spacer(GlanceModifier.width(8.dp))
                ActionButton("✓", busy == BUSY_MEAL, Saffron, busy == null, actionRunCallback<MarkEatenAction>())
            }
        }
    }

    /** Water as little glasses filling toward the target, with "+" for one more. */
    @Composable
    private fun WaterRow(day: WidgetSnapshot, waterMl: Int, busy: String?) {
        val glasses = ((day.waterTargetMl + 249) / 250).coerceIn(4, 10)
        val full = (waterMl / 250).coerceAtMost(glasses)
        Row(
            GlanceModifier.fillMaxWidth().background(ColorProvider(WaterSoft)).cornerRadius(14.dp)
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(GlanceModifier.defaultWeight()) {
                Text(
                    rtl(if (waterMl >= day.waterTargetMl) "💧 ${litres(waterMl)} لیتر · کامل شد" else "💧 ${litres(waterMl)} از ${litres(day.waterTargetMl)} لیتر"),
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(Water), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                )
                // One child per glass (Glance rows take at most 10 children), spacing inside each.
                Row {
                    repeat(glasses) { i ->
                        Box(GlanceModifier.padding(end = 3.dp)) {
                            Box(
                                GlanceModifier.size(width = 8.dp, height = 11.dp).cornerRadius(2.dp)
                                    .background(ColorProvider(if (i < full) Water else WaterEmpty)),
                            ) {}
                        }
                    }
                }
            }
            ActionButton("+", busy == BUSY_WATER, Water, busy == null, actionRunCallback<AddWaterAction>(), small = true)
        }
    }

    @Composable
    private fun ActionButton(label: String, busy: Boolean, color: Color, enabled: Boolean, action: Action, small: Boolean = false) {
        val side = if (small) 30.dp else 40.dp
        val base = GlanceModifier.size(side).background(ColorProvider(color)).cornerRadius(side / 2)
        Box(if (enabled) base.clickable(action) else base, contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(GlanceModifier.size(side - 14.dp), color = ColorProvider(Color.White))
            } else {
                Text(label, style = TextStyle(color = ColorProvider(Color.White), fontSize = if (small) 17.sp else 20.sp, fontWeight = FontWeight.Bold))
            }
        }
    }

    companion object {
        internal const val BUSY_MEAL = "meal"
        internal const val BUSY_WATER = "water"
        private const val PREFS = "momentum.widget"
        private val json = Json { ignoreUnknownKeys = true }
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        internal val SnapshotKey = stringPreferencesKey("snapshot")
        internal val WaterKey = stringPreferencesKey("water")
        internal val BusyKey = stringPreferencesKey("busy")

        private fun encode(snapshot: WidgetSnapshot) = json.encodeToString(WidgetSnapshot.serializer(), snapshot)
        private fun decode(text: String) = runCatching { json.decodeFromString(WidgetSnapshot.serializer(), text) }.getOrNull()

        private fun local(context: Context) = context.getSharedPreferences("momentum.local", Context.MODE_PRIVATE)
        internal fun waterOf(context: Context, date: String) = local(context).getInt("water:$date", 0)

        fun load(context: Context): WidgetSnapshot? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("snapshot", null)?.let(::decode)

        private fun save(context: Context, snapshot: WidgetSnapshot?) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
                if (snapshot == null) remove("snapshot") else putString("snapshot", encode(snapshot))
            }.apply()
        }

        /** Writes into every placed widget's state, which redraws it. */
        internal suspend fun edit(context: Context, block: (MutablePreferences) -> Unit) {
            GlanceAppWidgetManager(context).getGlanceIds(MomentumWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { block(it) }
                MomentumWidget().update(context, id)
            }
        }

        private suspend fun push(context: Context, snapshot: WidgetSnapshot?) = edit(context) { prefs ->
            if (snapshot == null) {
                prefs.remove(SnapshotKey)
            } else {
                prefs[SnapshotKey] = encode(snapshot)
                prefs[WaterKey] = waterOf(context, snapshot.localDate).toString()
            }
        }

        /** Called by the app whenever the dashboard changes (null on sign-out). */
        fun saveSnapshot(context: Context, dashboard: Dashboard?) {
            val day = dashboard?.plan?.day
            val snapshot = if (dashboard == null || day == null) null else WidgetSnapshot(
                localDate = dashboard.localDate,
                meals = day.meals.mapNotNull { meal ->
                    val option = meal.chosenOption ?: return@mapNotNull null
                    WidgetMeal(meal.slotKey, meal.title, option.title, option.optionKey, meal.scheduledTime, meal.completionStatus)
                },
                waterTargetMl = (day.targets.waterMl ?: 2200.0).toInt(),
                timezone = dashboard.profile.timezone,
            )
            save(context, snapshot)
            scope.launch { runCatching { push(context, snapshot) } }
        }

        /** Called by the app when today's water changes there. */
        fun saveWater(context: Context, waterMl: Int) {
            scope.launch { runCatching { edit(context) { it[WaterKey] = waterMl.toString() } } }
        }

        internal suspend fun markLocal(context: Context, slotKey: String) {
            val snapshot = load(context) ?: return
            val next = snapshot.copy(meals = snapshot.meals.map { if (it.slotKey == slotKey) it.copy(status = "completed") else it })
            save(context, next)
            push(context, next)
        }

        /** Tells the app its cached day is stale, so it reloads when opened. */
        internal fun touchApp(context: Context) {
            local(context).edit().putLong("widget_changed_at", System.currentTimeMillis()).apply()
        }

        internal fun openApp(context: Context) {
            context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** The widget's "I ate it": spinner on, record the next meal through the API, then move on. */
class MarkEatenAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val snapshot = MomentumWidget.load(context) ?: return
        val meal = snapshot.meals.firstOrNull { it.status == "planned" } ?: return
        MomentumWidget.edit(context) { it[MomentumWidget.BusyKey] = MomentumWidget.BUSY_MEAL }
        val ok = runCatching { Api(context.applicationContext).completeMeal(snapshot.localDate, meal.slotKey, meal.optionKey) }.isSuccess
        MomentumWidget.edit(context) { it.remove(MomentumWidget.BusyKey) }
        if (ok) {
            MomentumWidget.markLocal(context, meal.slotKey)
            MomentumWidget.touchApp(context)
        } else {
            // Signed out, offline or the day changed: open the app, which shows the real reason.
            MomentumWidget.openApp(context)
        }
    }
}

/** The widget's "+ glass": counted on the phone at once, then saved to the server. */
class AddWaterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val snapshot = MomentumWidget.load(context) ?: return
        val date = snapshot.localDate
        val ml = (MomentumWidget.waterOf(context, date) + 250).coerceAtMost(6000)
        context.getSharedPreferences("momentum.local", Context.MODE_PRIVATE).edit().putInt("water:$date", ml).apply()
        MomentumWidget.edit(context) {
            it[MomentumWidget.BusyKey] = MomentumWidget.BUSY_WATER
            it[MomentumWidget.WaterKey] = ml.toString()
        }
        // The phone keeps the count either way; the server copy catches up when it can.
        runCatching { Api(context.applicationContext).saveWater(date, ml, snapshot.timezone) }
        MomentumWidget.edit(context) { it.remove(MomentumWidget.BusyKey) }
        MomentumWidget.touchApp(context)
    }
}

class MomentumWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MomentumWidget()
}
