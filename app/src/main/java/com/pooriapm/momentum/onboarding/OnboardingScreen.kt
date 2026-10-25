package com.pooriapm.momentum.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.Balance
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalDining
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.SelfImprovement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.BuildConfig
import com.pooriapm.momentum.ui.components.CheckRow
import com.pooriapm.momentum.ui.components.ChipGroup
import com.pooriapm.momentum.ui.components.ChoiceCard
import com.pooriapm.momentum.ui.components.ChoiceGroup
import com.pooriapm.momentum.ui.components.DecimalField
import com.pooriapm.momentum.ui.components.Dropdown
import com.pooriapm.momentum.ui.components.InfoCard
import com.pooriapm.momentum.ui.components.JalaliDateField
import com.pooriapm.momentum.ui.components.NumberStepper
import com.pooriapm.momentum.ui.components.StepScaffold
import com.pooriapm.momentum.ui.components.TextAnswer
import com.pooriapm.momentum.ui.components.YesNoQuestion
import com.pooriapm.momentum.ui.fa
import com.pooriapm.momentum.ui.isoToJalali
import com.pooriapm.momentum.ui.jalaliLabel
import com.pooriapm.momentum.ui.jalaliToIso
import com.pooriapm.momentum.ui.todayJalali
import kotlinx.coroutines.delay
import java.util.Locale

private val allergens = listOf(
    "milk" to "شیر", "egg" to "تخم‌مرغ", "peanut" to "بادام‌زمینی", "tree_nut" to "مغزها",
    "wheat" to "گندم / گلوتن", "soy" to "سویا", "fish" to "ماهی", "shellfish" to "صدف و میگو",
    "sesame" to "کنجد", "other" to "سایر",
)
// Saturday-first week, values match JavaScript getDay() as on the website.
private val weekdays = listOf("6" to "شنبه", "0" to "یکشنبه", "1" to "دوشنبه", "2" to "سه‌شنبه", "3" to "چهارشنبه", "4" to "پنجشنبه", "5" to "جمعه")
private val goalLabels = mapOf("fat_loss" to "کاهش چربی", "muscle_gain" to "عضله‌سازی", "maintenance" to "حفظ وزن و تناسب")
private val sexLabels = mapOf("female" to "زن", "male" to "مرد", "undisclosed" to "ترجیح می‌دهم نگویم")

@Composable
fun OnboardingScreen(state: OnboardingState) {
    LaunchedEffect(Unit) { if (!state.loaded) state.load() }
    state.loadError?.let { error ->
        // Never start from an empty form when the saved answers could not be read.
        com.pooriapm.momentum.ui.components.ErrorState(error, state::load)
        return
    }
    if (!state.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (state.phase != BuildPhase.Idle) {
        PlanBuildScreen(state)
        return
    }
    AnimatedContent(
        targetState = state.step,
        transitionSpec = { (slideInHorizontally { -it / 6 } + fadeIn()) togetherWith (slideOutHorizontally { it / 6 } + fadeOut()) },
        label = "step",
    ) { step -> StepContent(state, step) }
}

@Composable
private fun StepContent(state: OnboardingState, step: Step) {
    val a = state.answers
    val first = step == Step.Intro
    val primary = when (step) {
        Step.Intro -> "شروع کنیم"
        Step.Review -> "تأیید و ساخت برنامه"
        Step.Health -> if (healthOutcome(a) in setOf(HealthOutcome.Blocked, HealthOutcome.Urgent)) "ادامه ممکن نیست" else "ادامه"
        else -> "ادامه"
    }
    val (title, subtitle) = copyFor(step, a)
    StepScaffold(
        progress = state.progress,
        stepLabel = state.stepLabel.fa(),
        title = title,
        subtitle = subtitle,
        onBack = if (first) null else state::back,
        primaryLabel = primary,
        primaryEnabled = state.canContinue(),
        loading = state.saving,
        error = state.error ?: if (state.unsynced) "آخرین پاسخ‌ها هنوز در حساب ذخیره نشده‌اند؛ با قدم بعد دوباره ذخیره می‌شوند." else null,
        onPrimary = { if (step == Step.Review) state.finish() else state.next() },
    ) {
        when (step) {
            Step.Intro -> IntroStep()
            Step.Name -> TextAnswer("اسم کوچک", a["firstName"].orEmpty(), { state.set("firstName", it) }, maxLength = 120)
            Step.Goal -> ChoiceGroup {
                ChoiceCard("کاهش چربی", a["goalType"] == "fat_loss", { state.set("goalType", "fat_loss") }, "سبک‌تر شدن با حفظ عضله", Icons.AutoMirrored.Outlined.TrendingDown)
                ChoiceCard("عضله‌سازی", a["goalType"] == "muscle_gain", { state.set("goalType", "muscle_gain") }, "قوی‌تر شدن و افزایش حجم عضله", Icons.AutoMirrored.Outlined.TrendingUp)
                ChoiceCard("حفظ وزن و تناسب", a["goalType"] == "maintenance", { state.set("goalType", "maintenance") }, "سالم ماندن و انرژی بیشتر", Icons.Outlined.Balance)
            }
            Step.Sex -> ChoiceGroup {
                sexLabels.forEach { (value, label) -> ChoiceCard(label, a["sex"] == value, { state.set("sex", value) }) }
            }
            Step.Birth -> BirthStep(state)
            Step.BodySize -> {
                DecimalField("قد", a["heightCm"].orEmpty(), "سانتی‌متر", { state.set("heightCm", it) }, "بین ۱۲۰ تا ۲۳۰")
                DecimalField("وزن فعلی", a["weightKg"].orEmpty(), "کیلوگرم", { state.set("weightKg", it) }, "بین ۳۵ تا ۳۵۰")
            }
            Step.Target -> DecimalField("وزن هدف (اختیاری)", a["targetWeightKg"].orEmpty(), "کیلوگرم",
                { state.set("targetWeightKg", it) }, "اگر خالی بماند، برنامه با وزن فعلی‌ات شروع می‌شود.")
            Step.Summary -> SummaryStep(a)
            Step.Country -> CountryStep(state)
            Step.Health -> HealthStep(state)
            Step.Diet -> DietStep(state)
            Step.Foods -> {
                TextAnswer("غذاهایی که دوست داری", a["favoriteFoods"].orEmpty(), { state.set("favoriteFoods", it) }, "مثلاً قورمه‌سبزی، مرغ، ماست", 4000, 2)
                TextAnswer("غذاهایی که نمی‌خوری", a["dislikedFoods"].orEmpty(), { state.set("dislikedFoods", it) }, "مثلاً بادمجان، جگر", 4000, 2)
            }
            Step.Meals -> MealsStep(state)
            Step.Budget -> BudgetStep(state)
            Step.TrainingDays -> TrainingDaysStep(state)
            Step.TrainingStyle -> TrainingStyleStep(state)
            Step.TrainingTime -> TrainingTimeStep(state)
            Step.Schedule -> TextAnswer("ساعت کار و محدودیت آشپزی", a["workSchedule"].orEmpty(), { state.set("workSchedule", it) },
                "مثلاً شنبه تا چهارشنبه ۹ تا ۱۷، آشپزی بعد از ۱۸", 1000, 3)
            Step.Consent -> ConsentStep(state)
            Step.Review -> ReviewStep(state)
        }
    }
}

/** Title and the short "why we ask" line under it (the pattern Noom popularized). */
private fun copyFor(step: Step, a: Answers): Pair<String, String?> = when (step) {
    Step.Intro -> "بیا برنامه این ماهت را بسازیم" to null
    Step.Name -> "اسمت چیست؟" to "برنامه و پیام‌ها با اسم خودت نوشته می‌شوند."
    Step.Goal -> "هدف اصلی‌ات در این ماه چیست؟" to "غذا و تمرین هر روز از همین هدف چیده می‌شود."
    Step.Sex -> "جنسیت برای محاسبه‌ها" to "فقط برای محاسبه کالری و نیاز بدن استفاده می‌شود."
    Step.Birth -> "تاریخ تولدت" to "Momentum برای افراد ۱۸ سال و بالاتر است. سن در محاسبه انرژی روزانه هم اثر دارد."
    Step.BodySize -> "قد و وزن" to "نقطه شروع برنامه و نمودار پیشرفتت."
    Step.Target -> "وزن هدفت چقدر است؟" to "یک عدد واقع‌بینانه برای چند ماه آینده؛ برنامه قدم‌به‌قدم به آن نزدیک می‌شود."
    Step.Summary -> "${a["firstName"].orEmpty()}، اینجا هستی" to null
    Step.Country -> "کجا زندگی می‌کنی؟" to "غذاها از آشپزی و فروشگاه‌های همان‌جا انتخاب می‌شوند."
    Step.Health -> "چند سؤال ایمنی" to "پاسخ‌ها خصوصی می‌مانند و فقط تعیین می‌کنند ساخت خودکار برنامه برایت امن است یا نه."
    Step.Diet -> "سبک غذایی و حساسیت‌ها" to "چیزی که به آن حساسیت داری هیچ‌وقت در برنامه نمی‌آید."
    Step.Foods -> "سلیقه غذایی" to "ماه حول غذاهایی که دوست داری ساخته می‌شود. هر دو اختیاری است."
    Step.Meals -> "روزت چند وعده دارد؟" to "غذای روز بین همین تعداد وعده تقسیم می‌شود."
    Step.Budget -> "هزینه و غذای بیرون" to "مواد و فهرست خرید با همین سطح هزینه چیده می‌شود."
    Step.TrainingDays -> "هفته‌ای چند روز تمرین می‌کنی؟" to "صفر هم قبول است؛ برنامه فقط غذایی می‌شود."
    Step.TrainingStyle -> "کجا و چطور تمرین می‌کنی؟" to "حرکت‌ها با محل، نوع و سطح تجربه‌ات جور می‌شوند."
    Step.TrainingTime -> "زمان تمرین" to "تمرین‌ها در همین بازه جا می‌شوند."
    Step.Schedule -> "روزهایت چطور می‌گذرد؟" to "وعده‌ها و آشپزی با ساعت کارت هماهنگ می‌شوند."
    Step.Consent -> "رضایت‌ها" to "هر رضایت جداست و هر وقت بخواهی از حساب من قابل مدیریت است."
    Step.Review -> "مرور پاسخ‌ها" to "هر بخش را می‌توانی ویرایش کنی. بعد از تأیید، ساخت برنامه شروع می‌شود."
}

@Composable
private fun IntroStep() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("چند سؤال کوتاه درباره هدف، غذا و تمرینت؛ حدود سه دقیقه.", style = MaterialTheme.typography.bodyLarge)
        listOf(
            Icons.Outlined.LocalDining to "همه وعده‌های سی روز، با غذاهایی که دوست داری",
            Icons.Outlined.FitnessCenter to "تمرین‌های هر روز، با وقت و وسایل خودت",
            Icons.Outlined.CheckCircle to "پاسخ‌ها خودکار ذخیره می‌شوند؛ هر وقت برگردی همین‌جا ادامه می‌دهی",
        ).forEach { (icon, text) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
            }
        }
    }
}

@Composable
private fun BirthStep(state: OnboardingState) {
    val (thisYear, _, _) = todayJalali()
    val saved = isoToJalali(state.answers["birthDate"])
    var year by remember { mutableStateOf(saved?.first) }
    var month by remember { mutableStateOf(saved?.second) }
    var day by remember { mutableStateOf(saved?.third) }
    JalaliDateField(year, month, day, (thisYear - 18) downTo (thisYear - 100)) { y, m, d ->
        year = y; month = m; day = d
        state.set("birthDate", if (y != null && m != null && d != null) jalaliToIso(y, m, d) else "")
    }
    state.answers["birthDate"]?.takeIf { it.isNotBlank() }?.let { iso ->
        val age = ageOf(iso)
        if (age != null && age < 18) Text("این سرویس برای افراد ۱۸ سال و بالاتر است.", color = MaterialTheme.colorScheme.error)
        else Text("تاریخ ثبت‌شده: ${jalaliLabel(iso)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SummaryStep(a: Answers) {
    val bmi = bmi(a)
    InfoCard("شاخص توده بدنی") {
        Text(bmi?.let { "%.1f".format(Locale.ROOT, it).fa() } ?: "—", style = MaterialTheme.typography.displaySmall)
        Text(
            when {
                bmi == null -> ""
                bmi < 18.5 -> "کمتر از محدوده معمول"
                bmi < 25 -> "در محدوده معمول"
                bmi < 30 -> "کمی بالاتر از محدوده معمول"
                else -> "بالاتر از محدوده معمول"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text("از قد ${a["heightCm"].orEmpty().fa()} سانتی‌متر و وزن ${a["weightKg"].orEmpty().fa()} کیلوگرم. این عدد تشخیص پزشکی نیست.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    InfoCard("هدف این ماه") {
        Text(goalLabels[a["goalType"]].orEmpty(), style = MaterialTheme.typography.titleLarge)
        a["targetWeightKg"]?.takeIf { it.isNotBlank() && a["goalType"] != "maintenance" }?.let {
            Text("از ${a["weightKg"].orEmpty().fa()} به ${it.fa()} کیلوگرم، با قدم‌های پایدار.")
        }
    }
    Text("حالا غذا و تمرینت را می‌پرسیم تا این هدف به برنامه روزانه تبدیل شود.", style = MaterialTheme.typography.bodyLarge)
}

private val countries: List<Pair<String, String>> by lazy {
    val fa = Locale.forLanguageTag("fa")
    Locale.getISOCountries().map { it to Locale.Builder().setRegion(it).build().getDisplayCountry(fa) }
        .sortedBy { it.second }
}

@Composable
private fun CountryStep(state: OnboardingState) {
    val current = state.answers["country"] ?: "IR"
    ChoiceGroup {
        ChoiceCard("ایران", current == "IR", { state.set("country", "IR") }, "آشپزی و فروشگاه‌های ایرانی")
        ChoiceCard("کشور دیگر", current != "IR", { if (current == "IR") state.set("country", "") })
    }
    if (current != "IR") {
        Dropdown("کشور", countries.firstOrNull { it.first == current }?.second, countries) { state.set("country", it) }
    }
}

private val injuryAreas = listOf(
    "knee" to "زانو", "ankle" to "مچ پا", "hip" to "لگن", "lower_back" to "کمر",
    "shoulder" to "شانه", "elbow" to "آرنج", "wrist" to "مچ دست", "neck" to "گردن",
)

@Composable
private fun HealthStep(state: OnboardingState) {
    val a = state.answers
    YesNoQuestion("باردار هستی یا شیر می‌دهی؟", a["pregnancyOrBreastfeeding"]) { state.set("pregnancyOrBreastfeeding", it) }
    YesNoQuestion("سابقه یا نگرانی اختلال خوردن داری؟", a["eatingDisorderHistory"]) { state.set("eatingDisorderHistory", it) }
    YesNoQuestion("دیابت، بیماری کلیه، کبد، قلب یا دارویی که روی وزن اثر دارد؟", a["highRiskCondition"]) { state.set("highRiskCondition", it) }
    YesNoQuestion("الان علامت شدید یا ناگهانی داری که کمک فوری لازم دارد؟", a["urgentSymptoms"]) { state.set("urgentSymptoms", it) }
    YesNoQuestion("آسیب یا محدودیت حرکتی داری؟", a["injuryLimitation"]) { state.set("injuryLimitation", it) }
    if (a["injuryLimitation"] == "yes") {
        Text("کدام قسمت بدن؟ (اختیاری)", style = MaterialTheme.typography.titleMedium)
        ChipGroup(injuryAreas, a.list("injuryAreas").toSet()) { state.toggle("injuryAreas", it) }
    }
    when (healthOutcome(a)) {
        HealthOutcome.Urgent -> InfoCard("اول کمک فوری بگیر") {
            Text("برای علامت شدید یا ناگهانی با اورژانس ۱۱۵ یا پزشک تماس بگیر. هیچ درخواستی مصرف نشده است.")
        }
        HealthOutcome.Blocked -> InfoCard("ساخت خودکار برنامه برایت مناسب نیست") {
            Text("این شرایط باید با یک متخصص سلامت بررسی شود. پاسخ‌هایت ذخیره است و هیچ درخواستی مصرف نشده است.")
        }
        else -> {
            TextAnswer("داروهای فعلی (اختیاری)", a["medications"].orEmpty(), { state.set("medications", it) }, minLines = 1)
            TextAnswer("ملاحظات پزشکی دیگر (اختیاری)", a["medicalNotes"].orEmpty(), { state.set("medicalNotes", it) }, minLines = 1)
            TextAnswer("مکمل‌های فعلی (اختیاری)", a["supplements"].orEmpty(), { state.set("supplements", it) }, minLines = 1)
        }
    }
}

@Composable
private fun DietStep(state: OnboardingState) {
    val a = state.answers
    ChoiceGroup {
        ChoiceCard("همه‌چیزخوار", a["dietStyle"] == "omnivore", { state.set("dietStyle", "omnivore") }, icon = Icons.Outlined.LocalDining)
        ChoiceCard("گیاه‌خوار", a["dietStyle"] == "vegetarian", { state.set("dietStyle", "vegetarian") }, icon = Icons.Outlined.Eco)
    }
    Text("حساسیت‌ها", style = MaterialTheme.typography.titleMedium)
    ChipGroup(allergens, a.list("allergies").toSet()) { state.toggle("allergies", it) }
    if ("other" in a.list("allergies")) {
        TextAnswer("حساسیت دیگر را بنویس", a["otherAllergy"].orEmpty(), { state.set("otherAllergy", it) }, maxLength = 160)
        Text("با «سایر»، ساخت خودکار برنامه تا بررسی دستی متوقف می‌ماند تا هیچ خطری نکنیم.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MealsStep(state: OnboardingState) {
    val a = state.answers
    NumberStepper("وعده در روز", a.int("requestedMealCount", 3), 2..6) { state.set("requestedMealCount", it.toString()) }
    NumberStepper("گزینه برای هر وعده", a.int("preferredOptionCount", 3), 1..4) { state.set("preferredOptionCount", it.toString()) }
    Text("گزینه بیشتر یعنی تنوع بیشتر؛ کمتر یعنی روز ساده‌تر.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    TextAnswer("الگوی وعده‌ها (اختیاری)", a["requestedMealPattern"].orEmpty(), { state.set("requestedMealPattern", it) }, "مثلاً ناهار مفصل و شام سبک", 500)
}

@Composable
private fun BudgetStep(state: OnboardingState) {
    val a = state.answers
    ChoiceGroup {
        ChoiceCard("اقتصادی", a["foodBudget"] == "budget", { state.set("foodBudget", "budget") })
        ChoiceCard("معمولی", a["foodBudget"] == "standard", { state.set("foodBudget", "standard") })
        ChoiceCard("آزاد", a["foodBudget"] == "flexible", { state.set("foodBudget", "flexible") })
    }
    NumberStepper("غذای بیرون در هفته", a.int("restaurantMealsPerWeek"), 0..21) { state.set("restaurantMealsPerWeek", it.toString()) }
    if (a.int("restaurantMealsPerWeek") > 0) {
        TextAnswer("بیرون معمولاً چه می‌خوری؟", a["restaurantPreferences"].orEmpty(), { state.set("restaurantPreferences", it) }, "مثلاً کباب، ساندویچ", 4000)
    }
    TextAnswer("محدودیت آشپزی (اختیاری)", a["cookingConstraints"].orEmpty(), { state.set("cookingConstraints", it) }, "مثلاً حداکثر ۳۰ دقیقه، بدون فر", 4000)
}

@Composable
private fun TrainingDaysStep(state: OnboardingState) {
    val a = state.answers
    val days = a.int("trainingDays")
    NumberStepper("روز تمرین در هفته", days, 0..7) { next ->
        state.set("trainingDays", next.toString())
        val chosen = a.list("trainingWeekdays")
        if (chosen.size > next) state.set("trainingWeekdays", chosen.take(next).joinToString(","))
    }
    if (days > 0) {
        Text("کدام روزها؟ (${a.list("trainingWeekdays").size.fa()} از ${days.fa()})", style = MaterialTheme.typography.titleMedium)
        ChipGroup(weekdays, a.list("trainingWeekdays").toSet()) { state.toggle("trainingWeekdays", it, limit = days) }
    }
}

@Composable
private fun TrainingStyleStep(state: OnboardingState) {
    val a = state.answers
    Text("محل تمرین", style = MaterialTheme.typography.titleMedium)
    ChoiceGroup {
        ChoiceCard("خانه", a["trainingLocation"] == "home", { state.set("trainingLocation", "home") }, icon = Icons.Outlined.Home)
        ChoiceCard("باشگاه", a["trainingLocation"] == "gym", { state.set("trainingLocation", "gym") }, icon = Icons.Outlined.FitnessCenter)
        ChoiceCard("فضای باز", a["trainingLocation"] == "outdoor", { state.set("trainingLocation", "outdoor") }, "با وزن بدن، بدون وسیله", Icons.Outlined.Park)
    }
    if (a["trainingLocation"] == "home" || a["trainingLocation"] == "gym") {
        TextAnswer("وسایلی که داری (اختیاری)", a["equipment"].orEmpty(), { state.set("equipment", it) },
            if (a["trainingLocation"] == "home") "مثلاً دمبل، کش تمرینی" else "مثلاً رک اسکات، هالتر، کابل")
    }
    Text("نوع تمرین", style = MaterialTheme.typography.titleMedium)
    ChipGroup(listOf("strength" to "قدرتی", "crossfit" to "کراس‌فیت", "cardio" to "هوازی", "mixed" to "ترکیبی"),
        setOfNotNull(a["primaryActivity"])) { state.set("primaryActivity", it) }
    Text("سطح تجربه", style = MaterialTheme.typography.titleMedium)
    ChoiceGroup {
        ChoiceCard("تازه‌کار", a["trainingExperience"] == "beginner", { state.set("trainingExperience", "beginner") }, icon = Icons.Outlined.SelfImprovement)
        ChoiceCard("متوسط", a["trainingExperience"] == "intermediate", { state.set("trainingExperience", "intermediate") }, icon = Icons.AutoMirrored.Outlined.DirectionsRun)
        ChoiceCard("پیشرفته", a["trainingExperience"] == "advanced", { state.set("trainingExperience", "advanced") }, icon = Icons.Outlined.FitnessCenter)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrainingTimeStep(state: OnboardingState) {
    val a = state.answers
    Text("مدت هر جلسه", style = MaterialTheme.typography.titleMedium)
    ChipGroup(listOf("30", "45", "60", "75", "90", "120").map { it to "${it.fa()} دقیقه" } + ("custom" to "دلخواه"),
        setOfNotNull(a["trainingDurationPreset"])) { state.set("trainingDurationPreset", it) }
    if (a["trainingDurationPreset"] == "custom") {
        NumberStepper("مدت دلخواه", a.int("trainingDuration", 60), 15..120, "دقیقه") { state.set("trainingDuration", ((it / 5) * 5).coerceIn(15, 120).toString()) }
    }
    var picking by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
        Text(a["trainingStartTime"]?.takeIf { it.isNotBlank() }?.let { "ساعت شروع: ${it.fa()}" } ?: "انتخاب ساعت معمول شروع تمرین")
    }
    if (picking) {
        val initial = a["trainingStartTime"]?.split(":")
        val time = rememberTimePickerState(initial?.getOrNull(0)?.toIntOrNull() ?: 18, initial?.getOrNull(1)?.toIntOrNull() ?: 0, is24Hour = true)
        AlertDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.set("trainingStartTime", "%02d:%02d".format(Locale.ROOT, time.hour, time.minute))
                    picking = false
                }) { Text("تأیید") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("انصراف") } },
            text = { TimePicker(time) },
        )
    }
    TextAnswer("محدودیت زمانی و روز جایگزین", a["trainingAvailability"].orEmpty(), { state.set("trainingAvailability", it) },
        "مثلاً اگر دوشنبه نشد، سه‌شنبه عصر", 1000, 2)
}

@Composable
private fun ConsentStep(state: OnboardingState) {
    val context = LocalContext.current
    val a = state.answers
    fun open(path: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.SITE_URL.trimEnd('/')}/fa$path")))
    CheckRow("شرایط استفاده را خواندم و می‌پذیرم.", a["termsAccepted"] == "yes") { state.set("termsAccepted", if (it) "yes" else "") }
    TextButton(onClick = { open("/terms") }) { Text("خواندن شرایط استفاده") }
    CheckRow("سیاست حریم خصوصی و نگهداری داده را می‌پذیرم.", a["privacyAccepted"] == "yes") { state.set("privacyAccepted", if (it) "yes" else "") }
    TextButton(onClick = { open("/privacy") }) { Text("خواندن حریم خصوصی") }
    CheckRow("با پردازش داده سلامت برای شخصی‌سازی برنامه موافقم.", a["healthDataConsent"] == "yes") { state.set("healthDataConsent", if (it) "yes" else "") }
    Text("Momentum مراقبت پزشکی، تشخیص یا درمان نیست.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ReviewStep(state: OnboardingState) {
    val a = prepareForCompletion(state.answers)
    @Composable
    fun Row(title: String, target: Step, body: String) {
        InfoCard(title) {
            Text(body)
            TextButton(onClick = { state.edit(target) }) { Text("ویرایش") }
        }
    }
    Row("درباره تو", Step.Name, listOfNotNull(
        a["firstName"], sexLabels[a["sex"]], a["birthDate"]?.let(::jalaliLabel),
        a["heightCm"]?.let { "${it.fa()} سانتی‌متر" }, a["weightKg"]?.let { "${it.fa()} کیلوگرم" },
    ).joinToString(" · "))
    Row("هدف", Step.Goal, listOfNotNull(goalLabels[a["goalType"]], a["targetWeightKg"]?.let { "هدف ${it.fa()} کیلوگرم" }).joinToString(" · "))
    Row("ایمنی", Step.Health, listOfNotNull(
        if (healthOutcome(a) == HealthOutcome.Eligible) "مانع ایمنی ثبت نشده" else "نیاز به بررسی",
        a.list("injuryAreas").takeIf { a["injuryLimitation"] == "yes" && it.isNotEmpty() }
            ?.mapNotNull { v -> injuryAreas.firstOrNull { it.first == v }?.second }?.joinToString("، ")?.let { "آسیب: $it" },
    ).joinToString(" · "))
    Row("غذا", Step.Diet, listOfNotNull(
        if (a["dietStyle"] == "vegetarian") "گیاه‌خوار" else "همه‌چیزخوار",
        a.list("allergies").takeIf { it.isNotEmpty() }?.joinToString("، ") { v -> allergens.first { it.first == v }.second }?.let { "حساسیت: $it" },
        a["requestedMealPattern"], "${a["preferredOptionCount"].orEmpty().fa()} گزینه برای هر وعده",
    ).joinToString(" · "))
    Row("تمرین", Step.TrainingDays, if (a.int("trainingDays") == 0) "فعلاً تمرین ندارم" else listOfNotNull(
        "${a.int("trainingDays").fa()} روز در هفته",
        a["trainingDuration"]?.let { "${it.fa()} دقیقه" },
        a["trainingStartTime"]?.fa(),
    ).joinToString(" · "))
    Row("رضایت‌ها", Step.Consent, "شرایط، حریم خصوصی و رضایت داده سلامت پذیرفته شد")
}

private val buildStages = listOf(
    "داریم شرایط شخصی‌ت رو بررسی می‌کنیم",
    "داریم با توجه به غذاهای مورد علاقه‌ت رژیم غذایی درست می‌کنیم",
    "داریم وعده‌های این ماه رو کنار هم می‌چینیم",
    "داریم تمرین‌ها رو با وقت و وسایلت تنظیم می‌کنیم",
    "داریم روزهای تمرین و استراحت رو تو هفته‌ت جا می‌دیم",
    "داریم لیست خرید رو از همون غذاها درمیاریم",
    "داریم یه بار دیگه با حساسیت‌ها و محدودیت‌هات چک می‌کنیم",
    "برنامه اختصاصی‌ت تقریباً آماده‌ست",
)

/** "We're building your plan" with a checklist that advances while the job runs. */
@Composable
private fun PlanBuildScreen(state: OnboardingState) {
    var stage by remember { mutableIntStateOf(0) }
    val working = state.phase == BuildPhase.Completing || state.phase == BuildPhase.Generating
    LaunchedEffect(working) {
        while (working && stage < buildStages.lastIndex) {
            delay(6000)
            stage++
        }
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        when (state.phase) {
            BuildPhase.Ready -> {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(56.dp))
                Text("برنامه ۳۰ روزه‌ات آماده است", style = MaterialTheme.typography.headlineSmall)
                Button(onClick = state::done, modifier = Modifier.fillMaxWidth()) { Text("رفتن به امروز") }
            }
            BuildPhase.Waiting -> {
                Text("ساخت برنامه کمی بیشتر طول کشید", style = MaterialTheme.typography.headlineSmall)
                Text("می‌توانی برنامه را ببندی؛ کار ادامه دارد و برنامه دوم ساخته نمی‌شود. وقتی آماده شد در «امروز» می‌بینی‌اش.")
                Button(onClick = state::done, modifier = Modifier.fillMaxWidth()) { Text("باشه") }
                OutlinedButton(onClick = state::retryGeneration, modifier = Modifier.fillMaxWidth()) { Text("بررسی دوباره") }
            }
            BuildPhase.Blocked, BuildPhase.Failed -> {
                Text(if (state.phase == BuildPhase.Blocked) "یک قدم مانده" else "ساخت برنامه انجام نشد", style = MaterialTheme.typography.headlineSmall)
                Text(state.phaseMessage.orEmpty())
                if (state.phase == BuildPhase.Failed) {
                    // Same job key: trying again never starts a second plan.
                    Button(onClick = state::retryGeneration, modifier = Modifier.fillMaxWidth()) { Text("تلاش دوباره") }
                    OutlinedButton(onClick = state::done, modifier = Modifier.fillMaxWidth()) { Text("بعداً") }
                } else {
                    Button(onClick = state::done, modifier = Modifier.fillMaxWidth()) { Text("ادامه") }
                }
            }
            else -> {
                Text("در حال ساخت برنامه‌ات", style = MaterialTheme.typography.headlineSmall)
                buildStages.forEachIndexed { index, text ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        when {
                            index < stage -> Icon(Icons.Outlined.CheckCircle, contentDescription = "انجام شد", tint = MaterialTheme.colorScheme.tertiary)
                            index == stage -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            else -> Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                        }
                        Text(text, modifier = Modifier.padding(start = 12.dp),
                            color = if (index <= stage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("معمولاً یکی دو دقیقه طول می‌کشد. می‌توانی برنامه را ببندی؛ کار ادامه دارد.",
                    textAlign = TextAlign.Start, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
