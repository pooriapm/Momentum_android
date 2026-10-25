package com.pooriapm.momentum.ui

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.launch

/** One tooltip: what to light up ([target], a key set with [guideTarget]) and what to say about it. */
data class GuideStep(
    val title: String,
    val text: String,
    val target: String? = null,
    /** For targets inside a LazyColumn: the item to scroll to when it is not on screen yet. */
    val item: Int? = null,
)

/** A tour of one screen. Steps without a target are shown in the middle, over the dimmed screen. */
data class Guide(val id: String, val title: String, val steps: List<GuideStep>)

object Guides {
    val home = Guide("home", "امروز", listOf(
        GuideStep("صفحه امروز", "هر روز از اینجا شروع کن: همه‌ی کارهای امروزت همین‌جاست. چند قسمت مهمش را با هم ببینیم."),
        GuideStep("حلقه‌های امروز", "هر حلقه یک بخش برنامهٔ امروز است: وعده‌ها، تمرین و آب. حلقهٔ خط‌چین یعنی داده یا هدفش هنوز معلوم نیست.", "home_orbit", 0),
        GuideStep("قدم بعدی", "کار بعدی امروز: وعدهٔ بعدی یا تمرین.", "home_next", 1),
        GuideStep("آب امروز", "با + و − لیوان‌ها را پر کن. هر لیوان ۲۵۰ میلی‌لیتر است.", "home_water", 2),
        GuideStep("تمرین امروز", "روی هر حرکت بزن تا انیمیشن و روش انجامش را ببینی. با «شروع تمرین» ست‌ها را ثبت کن.", "home_workout", 4),
        GuideStep("گزارش روزانه", "هر روز یک دقیقه حال، خواب و وزنت را ثبت کن؛ برنامه‌ی ماه بعد بر اساس آن ساخته می‌شود.", "home_checkin", 6),
        GuideStep("وعده‌ها", "وقتی خوردی تیک بزن، اگر نخوردی «رد کردن». روی وعده بزن تا مواد، گزینه‌ها و پخت مشترک را ببینی. بیرون غذا می‌خوری؟ دکمه‌ی کنار عنوان را بزن.", "home_meals", 7),
    ))

    val plan = Guide("plan", "برنامه", listOf(
        GuideStep("روزهای برنامه", "دایره نشان می‌دهد روز چندم از سی روز را می‌بینی و نقطه‌اش جای امروز است. با فلش‌ها بین روزها برو.", "plan_days", 3),
        GuideStep("فهرست خرید", "مواد وعده‌های برنامه، با مقدار دقیق، اینجا جمع شده است.", "plan_shopping", 1),
        GuideStep("آماده‌سازی هفته", "غذاهای پختنی هفت روز آینده و موادی که چند بار تکرار می‌شوند. «آماده شد» خرید یا خوردن را ثبت نمی‌کند.", "plan_prep", 2),
        GuideStep("برنامه‌های قبلی", "ماه‌های قبلی‌ات هم اینجا می‌مانند.", "plan_history", 5),
    ))

    val progress = Guide("progress", "پیشرفت", listOf(
        GuideStep("مرور هفته", "وعده‌ها و تمرین‌های ثبت‌شدهٔ هر هفتهٔ برنامه، با گزارش‌های روزانهٔ همان هفته. هفته‌های قبل را از بالای کارت انتخاب کن.", "progress_review", 1),
        GuideStep("روند هفته‌ها", "هفته‌های برنامه کنار هم.", "progress_weekly", 2),
        GuideStep("نمودار وزن", "وزنت را در «گزارش روزانه» وارد کن تا نمودار ۳۰ روزه ساخته شود. خط‌چین وزن هدف توست.", "progress_weight", 3),
    ))

    val me = Guide("me", "حساب من", listOf(
        GuideStep("عضویت", "روزهای باقی‌مانده‌ی عضویتت اینجاست و از همین‌جا تمدیدش می‌کنی.", "me_membership", 1),
        GuideStep("ترجیحات و ویجت", "غذاها، حساسیت‌ها و روزهای تمرین را برای ماه بعد عوض کن، یا ویجت را به صفحه‌ی اصلی گوشی اضافه کن.", "me_settings", 2),
        GuideStep("حالت سبک", "اگر گوشی‌ات کند است، حالت سبک جلوه‌ها را کم می‌کند تا برنامه روان‌تر باشد.", "me_lite", 3),
        GuideStep("یادآوری‌ها", "یادآوری وعده‌ها و تمرین را روشن کن تا سر وقت خبرت کنیم.", "me_reminders", 4),
    ))

    val shopping = Guide("shopping", "فهرست خرید", listOf(
        GuideStep("فهرست خرید", "همه‌ی مواد وعده‌های برنامه‌ات، با مقدار دقیق و برای یک نفر، از روی خود برنامه جمع شده است."),
        GuideStep("هفته یا کل برنامه", "«هفته خرید» فقط چیزهای همین هفته است؛ «تا پایان برنامه» همه‌ی روزهای باقی‌مانده.", "shop_scope"),
        GuideStep("تیک بزن", "هر چیزی را خریدی تیک بزن تا به بخش «خریده‌شده» برود. اشتباه شد؟ از همان‌جا برش گردان.", "shop_list"),
        GuideStep("بفرست برای خرید", "فهرست مانده را برای کسی که خرید می‌کند بفرست.", "shop_share"),
    ))

    val checkin = Guide("checkin", "گزارش روزانه", listOf(
        GuideStep("گزارش روزانه", "یک دقیقه وقت می‌گیرد. برنامه‌ی این ماه را تغییر نمی‌دهد، ولی برنامه‌ی ماه بعد بر اساس آن ساخته می‌شود."),
        GuideStep("امتیاز ۱ تا ۵", "انرژی، حال، گرسنگی و ریکاوری را از ۱ (کم) تا ۵ (عالی) بزن. جواب درست یا غلط ندارد.", "checkin_scores"),
        GuideStep("خواب و درد", "ساعت خواب دیشب را وارد کن. اگر جایی درد دارد، شدت و جایش را بنویس.", "checkin_body"),
        GuideStep("وزن، اختیاری", "اگر خودت را وزن کردی واردش کن تا در نمودار «پیشرفت» بیاید.", "checkin_weight"),
    ))

    val workout = Guide("workout", "اجرای تمرین", listOf(
        GuideStep("حرکت‌ها", "به ترتیب انجامشان بده. روی تصویر یا «راهنما» بزن تا انیمیشن و روش درست حرکت را ببینی.", "run_exercise", 0),
        GuideStep("ست‌ها را تیک بزن", "بعد از هر ست دایره‌اش را بزن؛ تایمر استراحت خودش شروع می‌شود.", "run_sets", 0),
        GuideStep("درد دارم", "اگر وسط تمرین درد گرفتی این را بزن و ثبتش کن. توقف تمرین ایرادی ندارد.", "run_pain"),
        GuideStep("پایان تمرین", "وقتی همه‌ی حرکت‌ها انجام یا رد شدند، تمرین را تمام کن.", "run_finish"),
    ))

    val eatingOut = Guide("eating_out", "بیرون غذا می‌خورم", listOf(
        GuideStep("مهمانی یا رستوران؟", "اینجا راهنمای سفارش است: در رستوران‌های رایج چه بخوری که به برنامه‌ات نزدیک بماند."),
        GuideStep("پیشنهادها", "هر کارت چند پیشنهاد ساده دارد، با کالری تقریبی.", "eat_tips"),
        GuideStep("بعد از غذا", "در صفحه‌ی «امروز» همان وعده را تیک بزن یا رد کن تا حساب روزت درست بماند."),
    ))

    val nextMonth = Guide("next_month", "ماه بعد", listOf(
        GuideStep("ماه بعد", "هر برنامه سی روز است و نزدیک پایان ماه، برنامه‌ی ماه بعد ساخته می‌شود. یادداشت بگذار یا ترجیحاتت را عوض کن تا ماه بعد بهتر شود.", "next_month", 7),
    ))

    val preferences = Guide("preferences", "ترجیحات برنامه", listOf(
        GuideStep("ترجیحات برنامه", "تغییرهای اینجا در برنامه‌ی ماه بعد اعمال می‌شوند؛ برنامه‌ی این ماه همان‌طور می‌ماند."),
        GuideStep("حساسیت غذایی", "حساسیت‌ها را دقیق بزن؛ غذاهایی که آن مواد را دارند در برنامه‌ات نمی‌آیند.", "prefs_allergies"),
        GuideStep("روز و ساعت تمرین", "روزهایی که می‌توانی ورزش کنی، مدت و ساعتش را بگذار.", "prefs_training"),
    ))

    val widget = Guide("widget", "ویجت", listOf(
        GuideStep("ویجت صفحه اصلی", "وعده‌ی بعدی و آب امروز را بدون باز کردن برنامه روی صفحه‌ی اصلی گوشی می‌بینی."),
        GuideStep("ثبت از روی ویجت", "با ✓ وعده‌ی بعدی ثبت می‌شود و با + یک لیوان آب اضافه می‌شود."),
        GuideStep("اندازه دلخواه", "ویجت را بزرگ یا کوچک کن؛ در هر اندازه طرح خودش را دارد."),
    ))

    private fun prefs(context: Context) = context.getSharedPreferences("momentum.local", Context.MODE_PRIVATE)

    fun seen(context: Context, guide: Guide) = prefs(context).getBoolean("guide:${guide.id}", false)

    fun markSeen(context: Context, guide: Guide) {
        prefs(context).edit().putBoolean("guide:${guide.id}", true).commit()
    }
}

/** The parts of a screen a tour can light up, and the tour now showing over it. */
@Stable
class GuideController {
    internal val targets = mutableStateMapOf<String, Pair<LayoutCoordinates, BringIntoViewRequester>>()
    internal var tour by mutableStateOf<Tour?>(null)

    internal class Tour(val guide: Guide, val list: LazyListState?, val onDone: () -> Unit)

    fun start(guide: Guide, list: LazyListState? = null, onDone: () -> Unit = {}) {
        if (tour == null) tour = Tour(guide, list, onDone)
    }
}

val LocalGuides = staticCompositionLocalOf<GuideController?> { null }

/** Marks a part of the screen a tour step can light up. */
fun Modifier.guideTarget(key: String): Modifier = composed {
    val controller = LocalGuides.current ?: return@composed this
    val requester = remember { BringIntoViewRequester() }
    DisposableEffect(key) { onDispose { controller.targets.remove(key) } }
    bringIntoViewRequester(requester).onGloballyPositioned { coords ->
        if (controller.targets[key]?.first !== coords) controller.targets[key] = coords to requester
    }
}

/** Runs [guide] by itself the first time this screen appears (once per install). */
@Composable
fun GuideOnFirstVisit(guide: Guide, list: LazyListState? = null) {
    val controller = LocalGuides.current ?: return
    val context = LocalContext.current
    LaunchedEffect(guide.id) {
        if (!Guides.seen(context, guide)) controller.start(guide, list)
    }
}

/** The small "i" in a screen's header that replays its tour. */
@Composable
fun GuideInfoButton(guide: Guide, list: LazyListState? = null, modifier: Modifier = Modifier) {
    val controller = LocalGuides.current
    Box(
        modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button) { controller?.start(guide, list) }
            .semantics { contentDescription = "راهنمای ${guide.title}" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/**
 * Hosts tours over [content]: the screen dims, the step's target stays lit in a rounded hole, and
 * a tooltip beside it explains it, with "next" and "skip". Taps on the screen are blocked meanwhile.
 */
@Composable
fun GuideHost(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val controller = remember { GuideController() }
    val context = LocalContext.current
    var host by remember { mutableStateOf<LayoutCoordinates?>(null) }
    Box(modifier.onGloballyPositioned { host = it }) {
        CompositionLocalProvider(LocalGuides provides controller) { content() }
        val tour = controller.tour
        if (tour != null) {
            TourOverlay(controller, tour, host) {
                Guides.markSeen(context, tour.guide)
                controller.tour = null
                tour.onDone()
            }
        }
    }
}

@Composable
private fun BoxScope.TourOverlay(controller: GuideController, tour: GuideController.Tour, host: LayoutCoordinates?, finish: () -> Unit) {
    val steps = tour.guide.steps
    var index by remember(tour) { mutableIntStateOf(0) }
    var lit by remember(tour) { mutableStateOf(false) }
    var ready by remember(tour) { mutableStateOf(false) }
    val density = LocalDensity.current
    val reduceMotion = LocalPerf.current.reduceMotion
    val pad = with(density) { 8.dp.toPx() }
    val left = remember(tour) { Animatable(0f) }
    val top = remember(tour) { Animatable(0f) }
    val right = remember(tour) { Animatable(0f) }
    val bottom = remember(tour) { Animatable(0f) }

    // Each step: bring its target on screen, measure it, then move the hole there.
    LaunchedEffect(tour, index) {
        val step = steps[index]
        val hostCoords = host
        var rect: Rect? = null
        if (step.target != null && hostCoords != null) {
            if (controller.targets[step.target]?.first?.isAttached != true && step.item != null) {
                tour.list?.scrollToItem(step.item)
                repeat(3) { awaitFrame() }
            }
            val entry = controller.targets[step.target]
            if (entry != null && entry.first.isAttached) {
                fun measure() = hostCoords.localBoundingBoxOf(entry.first, clipBounds = false)
                var r = measure()
                // Keep room under the lit part for the tooltip (and above the tab bar).
                val room = hostCoords.size.height - with(density) { 230.dp.toPx() }
                if (r.top < 0f || r.bottom > room) {
                    val list = tour.list
                    if (list != null && step.item != null) {
                        list.scrollToItem(step.item)
                        list.scrollBy(-with(density) { 72.dp.toPx() })
                    } else {
                        runCatching { entry.second.bringIntoView() }
                    }
                    repeat(3) { awaitFrame() }
                    r = measure()
                }
                // A part with nothing to show today (an empty card) is explained in the middle instead.
                rect = r.takeIf { it.width > 4f && it.height > 4f }
            }
        }
        lit = rect != null
        if (rect != null) {
            val spec = tween<Float>(if (reduceMotion || !ready) 0 else 320, easing = FastOutSlowInEasing)
            launch { left.animateTo(rect.left - pad, spec) }
            launch { top.animateTo(rect.top - pad, spec) }
            launch { right.animateTo(rect.right + pad, spec) }
            launch { bottom.animateTo(rect.bottom + pad, spec) }
        }
        ready = true
    }

    if (!ready) return
    val scrim = Color.Black.copy(alpha = 0.62f)
    val ring = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .matchParentSize()
            .zIndex(10f)
            // Swallows every touch, so nothing underneath reacts while the tour is open.
            .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            if (!lit) {
                drawRect(scrim)
            } else {
                val hole = RoundRect(
                    Rect(Offset(left.value, top.value), Size(right.value - left.value, bottom.value - top.value)),
                    CornerRadius(18.dp.toPx()),
                )
                val path = Path().apply { addRoundRect(hole) }
                clipPath(path, ClipOp.Difference) { drawRect(scrim) }
                drawPath(path, ring, style = Stroke(2.dp.toPx()))
            }
        }
        val step = steps[index]
        Tooltip(
            spot = if (lit) Rect(left.value, top.value, right.value, bottom.value) else null,
            title = step.title,
            text = step.text,
            counter = "${(index + 1).fa()} از ${steps.size.fa()}",
            last = index == steps.lastIndex,
            onNext = { if (index < steps.lastIndex) index++ else finish() },
            onSkip = finish,
        )
    }
}

/** The bubble: below the lit part when there is room, otherwise above it, or centred without one. */
@Composable
private fun Tooltip(spot: Rect?, title: String, text: String, counter: String, last: Boolean, onNext: () -> Unit, onSkip: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val gap = with(LocalDensity.current) { 12.dp.roundToPx() }
    Layout(
        content = {
            Card(
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(counter, style = MaterialTheme.typography.labelMedium, color = colors.outline)
                        Spacer(Modifier.weight(1f))
                        if (!last) TextButton(onClick = onSkip) { Text("رد کردن") }
                        Button(onClick = onNext) { Text(if (last) "متوجه شدم" else "بعدی") }
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
    ) { measurables, constraints ->
        val card = measurables.first().measure(Constraints(maxWidth = constraints.maxWidth, maxHeight = constraints.maxHeight))
        layout(constraints.maxWidth, constraints.maxHeight) {
            val y = when {
                spot == null -> (constraints.maxHeight - card.height) / 2
                spot.bottom + gap + card.height <= constraints.maxHeight -> spot.bottom.toInt() + gap
                spot.top - gap - card.height >= 0 -> spot.top.toInt() - gap - card.height
                else -> (constraints.maxHeight - card.height).coerceAtLeast(0)
            }
            card.place(0, y)
        }
    }
}
