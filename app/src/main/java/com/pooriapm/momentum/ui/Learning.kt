package com.pooriapm.momentum.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.BuildConfig
import com.pooriapm.momentum.ui.components.InfoCard

/**
 * Learning (draft): short lessons about reading and using the plan, each with one practice
 * question. Behind BuildConfig.LEARNING_PREVIEW (never on in release). The content is unreviewed and
 * says so on screen. Practising is stored on the phone only and never touches the plan, meal logs,
 * workouts, shopping or membership.
 */
data class Lesson(
    val id: String,
    val title: String,
    val body: String,
    val question: String,
    val options: List<String>,
    val answer: Int,
    val explain: String,
)

// DRAFT content: written for the preview only, not reviewed by a nutrition or training expert.
private val draftLessons = listOf(
    Lesson(
        "read-amounts", "خواندن مقدارهای برنامه",
        "مقدار هر ماده در برنامه به همان شکلی است که نامش می‌گوید. اگر نوشته «برنج پخته»، وزن برنج بعد از پخت است، نه برنج خام.",
        "در برنامه «برنج قهوه‌ای پخته: ۱۵۰ گرم» آمده است. این عدد یعنی چه؟",
        listOf("وزن برنج بعد از پخت", "وزن برنج خام پیش از پخت"), 0,
        "نام ماده «پخته» است، پس ۱۵۰ گرم وزن برنج پخته است.",
    ),
    Lesson(
        "change-meal", "تغییر غذا بدون حدس",
        "اگر وعده‌ای چند گزینه دارد، با زدن گزینهٔ دیگر اول اثرش را می‌بینید: کالری روز و مواد خرید. تا تایید نکنید چیزی عوض نمی‌شود.",
        "بعد از «تایید تغییر» برای یک وعده، آن وعده چه وضعی دارد؟",
        listOf("خورده‌شده ثبت می‌شود", "همچنان ثبت‌نشده است تا خودتان ثبتش کنید"), 1,
        "تغییر غذا فقط غذای وعده را عوض می‌کند؛ خوردن را جدا ثبت می‌کنید.",
    ),
    Lesson(
        "shared-table", "سهم شما در پخت مشترک",
        "وقتی غذا را برای خانه می‌پزید، سهم شما همان مقدار برنامه است. بقیه فقط به فهرست خرید اضافه می‌شود.",
        "اگر پخت مشترک ۴ سهمی ثبت کنید، کالری وعدهٔ شما چه می‌شود؟",
        listOf("چهار برابر می‌شود", "تغییری نمی‌کند"), 1,
        "پخت مشترک مقدار پخت را تغییر می‌دهد، نه سهم و کالری شما را.",
    ),
    Lesson(
        "rest-day", "روز استراحت",
        "روزهای بدون تمرین بخشی از برنامه‌اند. حلقهٔ تمرین در این روزها «روز استراحت» را نشان می‌دهد، نه صفر.",
        "در روز استراحت، تمرین نکردن یعنی چه؟",
        listOf("طبق برنامه است", "یک جلسه از دست رفته است"), 0,
        "استراحت جزو برنامه است و چیزی جبران‌کردنی نیست.",
    ),
)

@Composable
fun LearningCard(state: AppState, modifier: Modifier = Modifier) {
    if (!BuildConfig.LEARNING_PREVIEW) return
    var open by rememberSaveable { mutableStateOf(false) }
    val practised = draftLessons.count { it.id in state.practised }
    InfoCard("یادگیری · پیش‌نویس", modifier.clickable(role = Role.Button) { open = true }) {
        Text("${practised.fa()} از ${draftLessons.size.fa()} درس تمرین شده", style = MaterialTheme.typography.bodyMedium)
        Text("محتوای آزمایشی و بازبینی‌نشده است و برنامهٔ شما را تغییر نمی‌دهد.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (open) LearningSheet(state) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LearningSheet(state: AppState, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("یادگیری", style = MaterialTheme.typography.headlineSmall)
            Text("پیش‌نویس · این درس‌ها هنوز توسط متخصص بازبینی نشده‌اند.",
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary)
            draftLessons.forEachIndexed { i, lesson ->
                if (i > 0) HorizontalDivider()
                LessonBlock(i + 1, lesson, lesson.id in state.practised) { state.markPractised(lesson.id) }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("بستن") }
        }
    }
}

@Composable
private fun LessonBlock(number: Int, lesson: Lesson, practised: Boolean, onCorrect: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var picked by rememberSaveable(lesson.id) { mutableStateOf<Int?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Text("${number.fa()}. ${lesson.title}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (practised) Text("تمرین شد", style = MaterialTheme.typography.labelMedium, color = colors.tertiary)
        }
        Text(lesson.body, style = MaterialTheme.typography.bodyMedium)
        Text(lesson.question, style = MaterialTheme.typography.bodyMedium, color = colors.primary)
        lesson.options.forEachIndexed { i, option ->
            val chosen = picked == i
            val border = when {
                !chosen -> colors.outlineVariant
                i == lesson.answer -> colors.tertiary
                else -> colors.secondary
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, border, RoundedCornerShape(12.dp))
                    .background(if (chosen) border.copy(alpha = 0.08f) else colors.surface)
                    .clickable(role = Role.RadioButton) {
                        picked = i
                        if (i == lesson.answer) onCorrect()
                    }.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(option, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (chosen) {
                    Spacer(Modifier.width(8.dp))
                    Text(if (i == lesson.answer) "درست" else "نه", style = MaterialTheme.typography.labelMedium, color = border)
                }
            }
        }
        picked?.let { Text(lesson.explain, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
    }
}

