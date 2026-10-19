package com.pooriapm.momentum.widget

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
import androidx.glance.layout.RowScope
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
import androidx.glance.color.ColorProvider
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

// Day and night pairs: the widget follows the phone's dark mode, like the app.
private val Cream = ColorProvider(day = Color(0xFFFBF6EE), night = Color(0xFF26231F))
private val Ink = ColorProvider(day = Color(0xFF1C1A17), night = Color(0xFFF4F1EA))
private val Muted = ColorProvider(day = Color(0xFF6E675D), night = Color(0xFFC2BBAE))
private val Saffron = ColorProvider(day = Color(0xFFA35E00), night = Color(0xFFF0A93B))
private val SaffronFill = ColorProvider(day = Color(0xFFA35E00), night = Color(0xFFC27A12))
private val Green = ColorProvider(day = Color(0xFF2B7A4B), night = Color(0xFF3FA36C))
private val Water = ColorProvider(day = Color(0xFF3DA5F4), night = Color(0xFF4FB0F5))
private val WaterSoft = ColorProvider(day = Color(0xFFE3F2FD), night = Color(0xFF1E2E3B))
private val WaterEmpty = ColorProvider(day = Color(0xFFC6E2F7), night = Color(0xFF2F4A60))
private val SaffronSoft = ColorProvider(day = Color(0xFFF6E7CF), night = Color(0xFF3E3018))
private val SkippedTone = ColorProvider(day = Color(0xFFB9B1A5), night = Color(0xFF5E5951))
private val OnFill = ColorProvider(day = Color.White, night = Color.White)

// One grid for every size: the same gap, radius, badge and button everywhere.
private val GAP = 8.dp
private val INNER_RADIUS = 16.dp
private val BADGE = 26.dp
private val BUTTON = 36.dp
private val BUTTON_SMALL = 32.dp

private val waterText = TextStyle(color = Water, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right)

/** Whether the launcher lays rows out right-to-left (a Persian phone) or left-to-right. */
private val LocalRtlDevice = staticCompositionLocalOf { false }

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
            val rtlDevice = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
            GlanceTheme { Content(snapshot, water, prefs[BusyKey], rtlDevice) }
        }
    }

    // One design per size, so the widget never looks empty: a strip, two rows, a card, a full day.
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, COMPACT, MEDIUM, LARGE))

    @Composable
    private fun Content(snapshot: WidgetSnapshot?, waterMl: Int, busy: String?, rtlDevice: Boolean) {
        val today = java.time.LocalDate.now().toString()
        val current = snapshot?.takeIf { it.localDate == today }
        val size = LocalSize.current
        CompositionLocalProvider(LocalRtlDevice provides rtlDevice) {
            Box(
                GlanceModifier.fillMaxSize().background(Cream).cornerRadius(24.dp)
                    .padding(if (size.height < MEDIUM.height) 10.dp else 14.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    current == null -> Text(
                        rtl("Momentum · برای دیدن برنامه امروز اپ را باز کن"),
                        style = TextStyle(color = Muted, fontSize = 12.sp, textAlign = TextAlign.Center),
                    )
                    size.height >= LARGE.height -> Large(current, waterMl, busy)
                    size.height >= MEDIUM.height -> Medium(current, waterMl, busy)
                    size.height >= COMPACT.height -> Compact(current, waterMl, busy)
                    else -> Small(current, waterMl, busy)
                }
            }
        }
    }

    /** One row: the next meal and water as text, with "✓" and "+" beside it. */
    @Composable
    private fun Small(day: WidgetSnapshot, waterMl: Int, busy: String?) {
        val next = day.meals.firstOrNull { it.status == "planned" }
        RtlRow(
            GlanceModifier.fillMaxWidth(),
            middle = {
                TextBlock(
                    GlanceModifier.defaultWeight(),
                    title = next?.option ?: "همه وعده‌ها ثبت شد 🎉",
                    subtitle = "💧 ${litres(waterMl)} از ${litres(day.waterTargetMl)} لیتر",
                    titleColor = if (next == null) Green else Ink,
                    titleSize = 14,
                )
            },
            end = {
                if (next != null) {
                    ActionButton("✓", busy == BUSY_MEAL, SaffronFill, busy == null, actionRunCallback<MarkEatenAction>(), BUTTON_SMALL)
                    Spacer(GlanceModifier.width(6.dp))
                }
                ActionButton("+", busy == BUSY_WATER, Water, busy == null, actionRunCallback<AddWaterAction>(), BUTTON_SMALL)
            },
        )
    }

    /** Two rows for a short, wide widget: the next meal with its tick, then water. */
    @Composable
    private fun Compact(day: WidgetSnapshot, waterMl: Int, busy: String?) {
        Column(GlanceModifier.fillMaxSize()) {
            NextMeal(day, busy, GlanceModifier.fillMaxWidth().defaultWeight(), card = false)
            Spacer(GlanceModifier.height(6.dp))
            WaterRow(day, waterMl, busy, slim = true)
        }
    }

    /** A card: header, the next meal, the day as an even strip, water. */
    @Composable
    private fun Medium(day: WidgetSnapshot, waterMl: Int, busy: String?) {
        Column(GlanceModifier.fillMaxSize()) {
            Header(day)
            Spacer(GlanceModifier.height(GAP))
            NextMeal(day, busy, GlanceModifier.fillMaxWidth().defaultWeight(), card = true)
            Spacer(GlanceModifier.height(GAP))
            DayStrip(day)
            Spacer(GlanceModifier.height(GAP))
            WaterRow(day, waterMl, busy)
        }
    }

    /** The whole day: header, every meal with its status badge, water. */
    @Composable
    private fun Large(day: WidgetSnapshot, waterMl: Int, busy: String?) {
        val nextKey = day.meals.firstOrNull { it.status == "planned" }?.slotKey
        Column(GlanceModifier.fillMaxSize()) {
            Header(day)
            Spacer(GlanceModifier.height(GAP))
            Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                day.meals.forEach { meal ->
                    val isNext = meal.slotKey == nextKey
                    // Each meal takes an equal share of the height, so a tall widget spreads out instead of leaving a gap.
                    Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                        val row = GlanceModifier.fillMaxWidth().cornerRadius(INNER_RADIUS)
                        RtlRow(
                            (if (isNext) row.background(SaffronSoft) else row).padding(horizontal = 10.dp, vertical = 6.dp),
                            // The next meal's badge is its tick button, in the same column as the other badges.
                            start = {
                                Box(GlanceModifier.size(BUTTON_SMALL), contentAlignment = Alignment.Center) {
                                    if (isNext) {
                                        ActionButton("✓", busy == BUSY_MEAL, SaffronFill, busy == null, actionRunCallback<MarkEatenAction>(), BUTTON_SMALL)
                                    } else {
                                        StatusBadge(meal.status)
                                    }
                                }
                            },
                            middle = {
                                TextBlock(
                                    GlanceModifier.defaultWeight(),
                                    title = meal.option,
                                    subtitle = "${meal.title}${meal.time?.let { " · ${it.fa()}" } ?: ""}",
                                    titleColor = if (meal.status == "planned") Ink else Muted,
                                    titleSize = 13,
                                    bold = isNext,
                                    subtitleSize = 10,
                                )
                            },
                        )
                    }
                }
            }
            Spacer(GlanceModifier.height(GAP))
            WaterRow(day, waterMl, busy, button = BUTTON_SMALL)
        }
    }

    /** Brand on the right, today's progress as a pill on the left. */
    @Composable
    private fun Header(day: WidgetSnapshot) {
        val done = day.meals.count { it.status == "completed" }
        RtlRow(
            GlanceModifier.fillMaxWidth(),
            start = {
                Text("Momentum", style = TextStyle(color = Saffron, fontSize = 14.sp, fontWeight = FontWeight.Bold))
            },
            middle = { Spacer(GlanceModifier.defaultWeight()) },
            end = {
                Box(GlanceModifier.background(SaffronSoft).cornerRadius(10.dp).padding(horizontal = 9.dp, vertical = 3.dp)) {
                    Text(
                        rtl("${done.fa()} از ${day.meals.size.fa()} وعده"),
                        style = TextStyle(color = Saffron, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    )
                }
            },
        )
    }

    /** The next meal, large, with its tick; a saffron card in the medium design. */
    @Composable
    private fun NextMeal(day: WidgetSnapshot, busy: String?, modifier: GlanceModifier, card: Boolean) {
        val next = day.meals.firstOrNull { it.status == "planned" }
        val box = if (card) modifier.background(SaffronSoft).cornerRadius(INNER_RADIUS).padding(horizontal = 12.dp) else modifier
        Box(box, contentAlignment = Alignment.Center) {
            RtlRow(
                GlanceModifier.fillMaxWidth(),
                    middle = {
                    if (next == null) {
                        TextBlock(GlanceModifier.defaultWeight(), title = "همه وعده‌های امروز ثبت شد 🎉", subtitle = null, titleColor = Green, titleSize = 15)
                    } else {
                        TextBlock(
                            GlanceModifier.defaultWeight(),
                            title = next.option,
                            subtitle = "${next.title}${next.time?.let { " · ${it.fa()}" } ?: ""}",
                            titleColor = Ink,
                            titleSize = 16,
                            subtitleFirst = true,
                        )
                    }
                },
                end = if (next == null) null else {
                    { ActionButton("✓", busy == BUSY_MEAL, SaffronFill, busy == null, actionRunCallback<MarkEatenAction>(), BUTTON) }
                },
            )
        }
    }

    /** The day's meals as equal segments across the width: green eaten, grey skipped, saffron next. */
    @Composable
    private fun DayStrip(day: WidgetSnapshot) {
        val nextKey = day.meals.firstOrNull { it.status == "planned" }?.slotKey
        val meals = day.meals.take(10).let { if (LocalRtlDevice.current) it else it.reversed() }
        Row(GlanceModifier.fillMaxWidth()) {
            meals.forEachIndexed { i, meal ->
                val color = when {
                    meal.status == "completed" -> Green
                    meal.status == "skipped" -> SkippedTone
                    meal.slotKey == nextKey -> Saffron
                    else -> SaffronSoft
                }
                Box(GlanceModifier.defaultWeight().padding(start = if (i == 0) 0.dp else 3.dp, end = if (i == meals.lastIndex) 0.dp else 3.dp)) {
                    Box(GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp).background(color)) {}
                }
            }
        }
    }

    /**
     * Water as glasses spread evenly across the width, filling from the right, with "+" for one more.
     * The slim version puts the amount beside the glasses, on one line.
     */
    @Composable
    private fun WaterRow(day: WidgetSnapshot, waterMl: Int, busy: String?, slim: Boolean = false, button: Dp = BUTTON) {
        val done = waterMl >= day.waterTargetMl
        val amount = if (done) "💧 ${litres(waterMl)} لیتر · کامل شد" else "💧 ${litres(waterMl)} از ${litres(day.waterTargetMl)} لیتر"
        RtlRow(
            GlanceModifier.fillMaxWidth().background(WaterSoft).cornerRadius(INNER_RADIUS)
                .padding(horizontal = 10.dp, vertical = if (slim) 5.dp else 8.dp),
            start = if (!slim) null else {
                { Text(rtl(if (done) "💧 کامل شد" else "💧 ${litres(waterMl)} از ${litres(day.waterTargetMl)}"), maxLines = 1, style = waterText) }
            },
            middle = {
                Column(GlanceModifier.defaultWeight()) {
                    if (!slim) {
                        Text(rtl(amount), maxLines = 1, modifier = GlanceModifier.fillMaxWidth(), style = waterText)
                        Spacer(GlanceModifier.height(5.dp))
                    }
                    Glasses(day, waterMl)
                }
            },
            end = { ActionButton("+", busy == BUSY_WATER, Water, busy == null, actionRunCallback<AddWaterAction>(), if (slim) 28.dp else button) },
        )
    }

    @Composable
    private fun Glasses(day: WidgetSnapshot, waterMl: Int) {
        val glasses = ((day.waterTargetMl + 249) / 250).coerceIn(4, 10)
        val full = (waterMl / 250).coerceAtMost(glasses)
        val rtlDevice = LocalRtlDevice.current
        // One child per glass (Glance rows take at most 10 children), spacing inside each.
        Row(GlanceModifier.fillMaxWidth()) {
            repeat(glasses) { i ->
                val filled = if (rtlDevice) i < full else glasses - 1 - i < full
                Box(GlanceModifier.defaultWeight().padding(start = if (i == 0) 0.dp else 2.dp, end = if (i == glasses - 1) 0.dp else 2.dp)) {
                    Box(
                        GlanceModifier.fillMaxWidth().height(12.dp).cornerRadius(3.dp)
                            .background(if (filled) Water else WaterEmpty),
                    ) {}
                }
            }
        }
    }

    /** Eaten ✓ on green, skipped – on grey, still to come • on pale saffron. */
    @Composable
    private fun StatusBadge(status: String) {
        val (glyph, bg, fg) = when (status) {
            "completed" -> Triple("✓", Green, OnFill)
            "skipped" -> Triple("–", SkippedTone, OnFill)
            else -> Triple("•", SaffronSoft, Saffron)
        }
        Box(GlanceModifier.size(BADGE).cornerRadius(BADGE / 2).background(bg), contentAlignment = Alignment.Center) {
            Text(glyph, style = TextStyle(color = fg, fontSize = 13.sp, fontWeight = FontWeight.Bold))
        }
    }

    /** A title and a muted line, right-aligned whatever the phone's language. */
    @Composable
    private fun TextBlock(
        modifier: GlanceModifier,
        title: String,
        subtitle: String?,
        titleColor: ColorProvider,
        titleSize: Int,
        bold: Boolean = true,
        subtitleFirst: Boolean = false,
        subtitleSize: Int = 11,
    ) {
        Column(modifier) {
            if (subtitleFirst && subtitle != null) Subtitle(subtitle, subtitleSize)
            Text(
                rtl(title),
                maxLines = 1,
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(
                    color = titleColor,
                    fontSize = titleSize.sp,
                    fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Right,
                ),
            )
            if (!subtitleFirst && subtitle != null) Subtitle(subtitle, subtitleSize)
        }
    }

    @Composable
    private fun Subtitle(text: String, size: Int) {
        Text(
            rtl(text),
            maxLines = 1,
            modifier = GlanceModifier.fillMaxWidth(),
            style = TextStyle(color = Muted, fontSize = size.sp, textAlign = TextAlign.Right),
        )
    }

    /**
     * A row laid out right-to-left on any phone: [start] on the right, [end] on the left. The launcher
     * uses the phone's language, so on an English phone the children are placed in reverse. A gap is
     * placed between the parts, so it always falls on the inner side.
     */
    @Composable
    private fun RtlRow(
        modifier: GlanceModifier,
        start: (@Composable () -> Unit)? = null,
        middle: @Composable RowScope.() -> Unit,
        end: (@Composable () -> Unit)? = null,
    ) {
        val rtlDevice = LocalRtlDevice.current
        val left = if (rtlDevice) start else end
        val right = if (rtlDevice) end else start
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            if (left != null) {
                Row(verticalAlignment = Alignment.CenterVertically) { left() }
                Spacer(GlanceModifier.width(GAP))
            }
            middle()
            if (right != null) {
                Spacer(GlanceModifier.width(GAP))
                Row(verticalAlignment = Alignment.CenterVertically) { right() }
            }
        }
    }

    @Composable
    private fun ActionButton(label: String, busy: Boolean, color: ColorProvider, enabled: Boolean, action: Action, side: Dp) {
        val base = GlanceModifier.size(side).background(color).cornerRadius(side / 2)
        Box(if (enabled) base.clickable(action) else base, contentAlignment = Alignment.Center) {
            if (busy) {
                CircularProgressIndicator(GlanceModifier.size(side - 14.dp), color = OnFill)
            } else {
                Text(label, style = TextStyle(color = OnFill, fontSize = (side.value * 0.5f).sp, fontWeight = FontWeight.Bold))
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
