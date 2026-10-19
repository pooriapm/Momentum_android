package com.pooriapm.momentum.ui

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DoNotDisturbOn
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Healing
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.ArrowCircleLeft
import androidx.compose.material.icons.outlined.Mood
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class GuideStep(val icon: ImageVector, val title: String, val text: String)

/** A short tour of one screen: what it is for and how to use it, one step per page. */
data class Guide(val id: String, val title: String, val steps: List<GuideStep>)

object Guides {
    val home = Guide("home", "صفحه امروز", listOf(
        GuideStep(Icons.Outlined.TaskAlt, "روز تو در یک نگاه",
            "هر روز با این صفحه شروع کن: همه‌ی کارهای امروز، از وعده‌ها تا آب و تمرین، همین‌جاست."),
        GuideStep(Icons.Outlined.LocalFireDepartment, "زنجیره روزها",
            "هر روزی که حداقل یک وعده را ثبت کنی، شعله روشن می‌ماند و زنجیره‌ات یک روز بلندتر می‌شود."),
        GuideStep(Icons.Outlined.PieChart, "کالری و پروتئین",
            "کارت زعفرانی نشان می‌دهد چقدر از هدف امروز را خورده‌ای. با هر تیک وعده، خودش به‌روز می‌شود."),
        GuideStep(Icons.Outlined.ArrowCircleLeft, "قدم بعدی",
            "همیشه می‌گوید الان چه کاری مانده: وعده‌ی بعدی، تمرین یا اینکه روزت کامل شده."),
        GuideStep(Icons.Outlined.WaterDrop, "آب امروز",
            "با + و − لیوان‌ها را پر یا خالی کن. هر لیوان ۲۵۰ میلی‌لیتر است و هدف روزت را برنامه تعیین کرده."),
        GuideStep(Icons.Outlined.FitnessCenter, "تمرین امروز",
            "روی هر حرکت بزن تا انیمیشن و روش انجامش را ببینی. با «شروع تمرین» ست‌ها را قدم‌به‌قدم ثبت کن."),
        GuideStep(Icons.Outlined.CheckCircle, "ثبت وعده‌ها",
            "وقتی وعده‌ای را خوردی تیکش را بزن؛ اگر نخوردی «رد کردن». روی خود وعده بزن تا مواد، طرز تهیه و گزینه‌های دیگرش را ببینی."),
    ))

    val plan = Guide("plan", "برنامه ماه", listOf(
        GuideStep(Icons.Outlined.CalendarMonth, "سی روز، یکی‌یکی",
            "دایره نشان می‌دهد روز چندم از برنامه را می‌بینی. نقطه‌ی روی دایره جای امروز است."),
        GuideStep(Icons.Outlined.TouchApp, "رفتن بین روزها",
            "با فلش‌های دو طرف دایره به روزهای قبل و بعد برو تا وعده‌ها و تمرین آن روز را ببینی. تیک زدن فقط برای امروز است."),
        GuideStep(Icons.Outlined.ShoppingBasket, "فهرست خرید",
            "از کارت بالای صفحه فهرست خرید را باز کن؛ از روی همین برنامه ساخته می‌شود."),
        GuideStep(Icons.Outlined.History, "برنامه‌های قبلی",
            "ماه‌های قبلی‌ات هم اینجا می‌مانند تا ببینی از کجا شروع کرده‌ای."),
    ))

    val progress = Guide("progress", "پیشرفت", listOf(
        GuideStep(Icons.Outlined.Insights, "نمره نیست، روند است",
            "اینجا هر هفته با هفته‌ی قبلش مقایسه می‌شود: چند وعده و چند تمرین را انجام داده‌ای."),
        GuideStep(Icons.Outlined.MonitorWeight, "نمودار وزن",
            "وزنت را در «گزارش روزانه» وارد کن تا نمودار ۳۰ روزه ساخته شود. خط‌چین، وزن هدف توست."),
        GuideStep(Icons.Outlined.TaskAlt, "صبور باش",
            "وزن روزبه‌روز بالا و پایین می‌رود؛ جهت کلی نمودار در چند هفته مهم است، نه یک روز."),
    ))

    val me = Guide("me", "حساب من", listOf(
        GuideStep(Icons.Outlined.WorkspacePremium, "عضویت",
            "روزهای باقی‌مانده‌ی عضویتت اینجاست و از همین‌جا می‌توانی آن را از کافه‌بازار تمدید کنی."),
        GuideStep(Icons.Outlined.Tune, "ترجیحات برنامه",
            "غذاهایی که دوست نداری، حساسیت‌ها و روزها و ساعت تمرین را عوض کن تا برنامه‌ی بعدی دقیق‌تر شود."),
        GuideStep(Icons.Outlined.NotificationsActive, "یادآوری‌ها",
            "یادآوری وعده‌ها و تمرین را روشن کن تا سر وقت خبرت کنیم."),
        GuideStep(Icons.Outlined.Speed, "حالت سبک",
            "اگر گوشی‌ات کند است یا باتری کم داری، حالت سبک جلوه‌ها را کم می‌کند تا برنامه روان‌تر باشد."),
    ))

    val shopping = Guide("shopping", "فهرست خرید", listOf(
        GuideStep(Icons.Outlined.ShoppingBasket, "این فهرست چیست؟",
            "همه‌ی موادی که وعده‌های برنامه‌ات لازم دارند، با مقدار دقیق و برای یک نفر، از روی خود برنامه جمع شده."),
        GuideStep(Icons.Outlined.CalendarMonth, "هفته یا کل برنامه",
            "«هفته خرید» فقط چیزهای همین هفته را نشان می‌دهد؛ «تا پایان برنامه» همه‌ی روزهای باقی‌مانده را."),
        GuideStep(Icons.Outlined.CheckCircle, "تیک بزن",
            "هر چیزی را خریدی تیک بزن تا به بخش «خریده‌شده» برود. اشتباه زدی؟ از همان بخش برش گردان."),
        GuideStep(Icons.Outlined.Share, "بفرست برای خرید",
            "با دکمه‌ی اشتراک بالای صفحه، فهرست مانده را برای کسی که خرید می‌کند بفرست."),
    ))

    val checkin = Guide("checkin", "گزارش روزانه", listOf(
        GuideStep(Icons.Outlined.EditNote, "این گزارش برای چیست؟",
            "یک دقیقه وقت می‌گیرد: حال امروزت را ثبت کن تا روندت دیده شود و برنامه‌ی ماه بعد بر اساس آن ساخته شود."),
        GuideStep(Icons.Outlined.Mood, "امتیاز ۱ تا ۵",
            "انرژی، حال، گرسنگی و ریکاوری را از ۱ (کم) تا ۵ (عالی) انتخاب کن. جواب درست یا غلط ندارد."),
        GuideStep(Icons.Outlined.Bedtime, "خواب و درد",
            "ساعت خواب دیشب را وارد کن. اگر جایی درد دارد، شدتش و جایش را بنویس."),
        GuideStep(Icons.Outlined.MonitorWeight, "وزن (اختیاری)",
            "اگر خودت را وزن کردی واردش کن تا در نمودار «پیشرفت» بیاید. برنامه‌ی این ماه با این گزارش عوض نمی‌شود."),
    ))

    val workout = Guide("workout", "اجرای تمرین", listOf(
        GuideStep(Icons.Outlined.PlayCircle, "تمرین قدم‌به‌قدم",
            "حرکت‌ها به ترتیب آمده‌اند. روی تصویر یا «راهنما» بزن تا انیمیشن و روش درست هر حرکت را ببینی."),
        GuideStep(Icons.Outlined.CheckCircle, "ست‌ها را تیک بزن",
            "بعد از هر ست، دایره‌ی همان ست را بزن. وقتی همه‌ی ست‌ها تیک خوردند، حرکت تمام می‌شود."),
        GuideStep(Icons.Outlined.Timer, "استراحت خودکار",
            "با هر تیک، تایمر استراحت خودش شروع می‌شود تا بدانی کی ست بعدی را بزنی."),
        GuideStep(Icons.Outlined.SwapHoriz, "جایگزین یا رد کردن",
            "اگر حرکتی برایت ممکن نیست، حرکت جایگزین را انتخاب کن یا با گفتن دلیل ردش کن."),
        GuideStep(Icons.Outlined.Healing, "درد دارم",
            "اگر وسط تمرین درد گرفتی، «درد دارم» را بزن و ثبتش کن. به بدنت گوش بده؛ تمرین را متوقف کردن ایرادی ندارد."),
    ))

    val eatingOut = Guide("eating_out", "بیرون غذا می‌خورم", listOf(
        GuideStep(Icons.Outlined.Restaurant, "مهمانی یا رستوران؟",
            "اینجا راهنمای سفارش است: در رستوران‌های رایج چه بخوری که به برنامه‌ات نزدیک بماند."),
        GuideStep(Icons.Outlined.RestaurantMenu, "چطور سفارش بدهی",
            "هر کارت چند پیشنهاد ساده دارد، مثل نوع نان، سس یا اندازه‌ی پرس، با کالری تقریبی."),
        GuideStep(Icons.Outlined.CheckCircle, "بعد از غذا",
            "در صفحه‌ی «امروز» همان وعده را تیک بزن یا رد کن تا حساب روزت درست بماند."),
    ))

    val nextMonth = Guide("next_month", "ماه بعد", listOf(
        GuideStep(Icons.Outlined.CalendarMonth, "برنامه‌ها ماهانه‌اند",
            "هر برنامه سی روز است. نزدیک پایان ماه، برنامه‌ی ماه بعد برایت ساخته می‌شود."),
        GuideStep(Icons.Outlined.EditNote, "یادداشت برای ماه بعد",
            "هر چیزی که دوست داری ماه بعد فرق کند بنویس، مثلاً «صبحانه سبک‌تر» یا «تمرین کوتاه‌تر»."),
        GuideStep(Icons.Outlined.Tune, "ترجیحات",
            "برای تغییرهای بزرگ‌تر مثل حساسیت غذایی یا روزهای تمرین، «ترجیحات برنامه» را عوض کن."),
    ))

    val preferences = Guide("preferences", "ترجیحات برنامه", listOf(
        GuideStep(Icons.Outlined.Tune, "برنامه‌ات را شخصی کن",
            "اینجا چیزهایی را که برنامه با آن ساخته می‌شود عوض می‌کنی."),
        GuideStep(Icons.Outlined.DoNotDisturbOn, "حساسیت غذایی",
            "حساسیت‌ها را دقیق انتخاب کن؛ غذاهایی که آن مواد را دارند در برنامه‌ات نمی‌آیند."),
        GuideStep(Icons.Outlined.FitnessCenter, "روز و ساعت تمرین",
            "روزهایی که می‌توانی ورزش کنی، مدت هر جلسه و ساعتش را بگذار تا تمرین‌ها با زندگی‌ات جور شوند."),
        GuideStep(Icons.Outlined.TaskAlt, "کی اثر می‌کند؟",
            "بعد از «ذخیره»، تغییرها در برنامه‌ی ماه بعد اعمال می‌شوند؛ برنامه‌ی این ماه همان‌طور می‌ماند."),
    ))

    val widget = Guide("widget", "ویجت صفحه اصلی", listOf(
        GuideStep(Icons.Outlined.Widgets, "برنامه بدون باز کردن اپ",
            "ویجت وعده‌ی بعدی و آب امروز را روی صفحه‌ی اصلی گوشی نشان می‌دهد."),
        GuideStep(Icons.Outlined.CheckCircle, "تیک از روی صفحه اصلی",
            "با ✓ روی ویجت، وعده‌ی بعدی ثبت می‌شود و با + یک لیوان آب اضافه می‌شود؛ وقتی ذخیره می‌شود یک دایره‌ی چرخان می‌بینی."),
        GuideStep(Icons.Outlined.AddCircleOutline, "اندازه دلخواه",
            "ویجت را بزرگ یا کوچک کن؛ در هر اندازه طرح خودش را دارد، از یک نوار باریک تا کل روز."),
    ))

    private fun prefs(context: Context) = context.getSharedPreferences("momentum.local", Context.MODE_PRIVATE)

    fun seen(context: Context, guide: Guide) = prefs(context).getBoolean("guide:${guide.id}", false)

    fun markSeen(context: Context, guide: Guide) {
        prefs(context).edit().putBoolean("guide:${guide.id}", true).commit()
    }
}

/**
 * The "!" in a screen's header: opens the screen's guide, and opens it by itself the first time the
 * screen is shown (once per install).
 */
@Composable
fun GuideButton(guide: Guide, modifier: Modifier = Modifier, autoShow: Boolean = true) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(guide.id) {
        if (autoShow && !Guides.seen(context, guide)) open = true
    }
    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .size(34.dp)
            .clip(CircleShape)
            .border(1.5.dp, colors.primary.copy(alpha = 0.6f), CircleShape)
            .clickable(role = Role.Button) { open = true }
            .semantics { contentDescription = "راهنمای ${guide.title}" },
        contentAlignment = Alignment.Center,
    ) {
        Text("!", color = colors.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
    if (open) {
        GuideDialog(guide) {
            Guides.markSeen(context, guide)
            open = false
        }
    }
}

/** The guide, one step per page with dots, next/previous and "got it". */
@Composable
fun GuideDialog(guide: Guide, onDismiss: () -> Unit) {
    var page by rememberSaveable(guide.id) { mutableIntStateOf(0) }
    val last = guide.steps.lastIndex
    val colors = MaterialTheme.colorScheme
    val reduceMotion = LocalPerf.current.reduceMotion
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = colors.surface),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        ) {
            Column(Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "راهنمای ${guide.title}",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onDismiss) { Text("بستن") }
                }
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        if (reduceMotion) EnterTransition.None.togetherWith(ExitTransition.None)
                        else {
                            // Forward slides in from the left, as pages turn in right-to-left reading.
                            val dir = if (targetState > initialState) -1 else 1
                            (slideInHorizontally(tween(260)) { it / 4 * dir } + fadeIn(tween(260))).togetherWith(fadeOut(tween(120)))
                        }
                    },
                    label = "guide-page",
                ) { index ->
                    val step = guide.steps[index]
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = 230.dp).padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            Modifier.size(84.dp).clip(CircleShape).background(colors.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(step.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(40.dp))
                        }
                        Text(step.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                        Text(
                            step.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    guide.steps.indices.forEach { i ->
                        Box(
                            Modifier
                                .size(width = if (i == page) 18.dp else 7.dp, height = 7.dp)
                                .clip(CircleShape)
                                .background(if (i == page) colors.primary else colors.outlineVariant),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (page > 0) TextButton(onClick = { page-- }) { Text("قبلی") }
                    Spacer(Modifier.weight(1f))
                    Button(onClick = { if (page < last) page++ else onDismiss() }) {
                        Text(if (page < last) "بعدی" else "متوجه شدم")
                    }
                    Spacer(Modifier.width(2.dp))
                }
            }
        }
    }
}
