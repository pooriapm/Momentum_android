package com.pooriapm.momentum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.data.DailyCheckin
import com.pooriapm.momentum.data.Meal
import com.pooriapm.momentum.ui.components.LiquidSelector
import com.pooriapm.momentum.ui.theme.LocalGlow
import com.pooriapm.momentum.ui.theme.hueBackground
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.ui.semantics.Role
import com.pooriapm.momentum.ui.components.DecimalField
import com.pooriapm.momentum.ui.components.MealStatusControl
import com.pooriapm.momentum.ui.components.NumberStepper
import com.pooriapm.momentum.ui.components.TextAnswer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealSheet(state: AppState, meal: Meal, localDate: String, editable: Boolean, onDismiss: () -> Unit) {
    val option = meal.chosenOption ?: return
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(meal.title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(option.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${option.nutrition.calories.faRounded()} کالری · پروتئین ${option.nutrition.proteinG.faRounded()} · " +
                    "کربوهیدرات ${option.nutrition.carbsG.faRounded()} · چربی ${option.nutrition.fatG.faRounded()} گرم",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            option.warnings.forEach { Text(it, color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall) }
            if (option.ingredients.isNotEmpty()) {
                HorizontalDivider()
                Text("مواد لازم", style = MaterialTheme.typography.titleMedium)
                option.ingredients.forEach { Text("• ${it.name}: ${formatAmount(it.amount)} ${unitLabel(it.unit)}") }
            }
            option.recipe?.let { recipe ->
                HorizontalDivider()
                Text("طرز تهیه · ${(recipe.prepMinutes + recipe.cookMinutes).fa()} دقیقه", style = MaterialTheme.typography.titleMedium)
                recipe.steps.forEachIndexed { index, step -> Text("${(index + 1).fa()}. $step") }
            }
            if (editable) {
                // Stays open so the tick animation plays here; the sheet shows the live meal.
                MealStatusControl(
                    status = meal.completionStatus,
                    busy = false,
                    enabled = !state.mealPending(localDate, meal.slotKey),
                    onEat = { state.completeMeal(meal, localDate) },
                    onSkip = { state.skipMeal(meal, localDate) },
                    onUndo = { state.undoMeal(meal, localDate) },
                )
            }
        }
    }
}

private fun formatAmount(value: Double): String = value.faDecimal()

@Composable
private fun ScoreQuestion(label: String, low: String, high: String, value: Int, backdrop: Backdrop, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        LiquidSelector(
            count = 5,
            selected = (value - 1).coerceIn(0, 4),
            onSelect = { onChange(it + 1) },
            backdrop = backdrop,
            height = 52.dp,
            role = Role.RadioButton,
        ) { index ->
            Text((index + 1).fa(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        Text("۱ = $low · ۵ = $high", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Daily check-in in a sheet: quick 1–5 taps, sleep and optional weight. Never regenerates the month. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckinSheet(state: AppState, onDismiss: () -> Unit) {
    var energy by rememberSaveable { mutableIntStateOf(3) }
    var mood by rememberSaveable { mutableIntStateOf(3) }
    var hunger by rememberSaveable { mutableIntStateOf(3) }
    var recovery by rememberSaveable { mutableIntStateOf(3) }
    var sleepHours by rememberSaveable { mutableIntStateOf(7) }
    var pain by rememberSaveable { mutableIntStateOf(0) }
    var painWhere by rememberSaveable { mutableStateOf("") }
    var weight by rememberSaveable { mutableStateOf("") }
    val glow = LocalGlow.current
    // The score selectors are Liquid Glass: they refract the sheet's hue background behind them.
    val backdrop = rememberLayerBackdrop()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = glow.base,
        // The handle is drawn inside the hue so the background has no seam under it.
        dragHandle = null,
    ) {
        Box(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Box(Modifier.matchParentSize().glassSource(backdrop).hueBackground(glow))
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BottomSheetDefaults.DragHandle() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("گزارش امروز", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                    GuideButton(Guides.checkin)
                }
                ScoreQuestion("انرژی", "خیلی کم", "عالی", energy, backdrop) { energy = it }
                ScoreQuestion("حال‌وحوصله", "بد", "خیلی خوب", mood, backdrop) { mood = it }
                ScoreQuestion("گرسنگی", "اصلاً", "خیلی زیاد", hunger, backdrop) { hunger = it }
                ScoreQuestion("ریکاوری بدن", "خسته", "کاملاً آماده", recovery, backdrop) { recovery = it }
                NumberStepper("خواب دیشب", sleepHours, 0..14, "ساعت") { sleepHours = it }
                NumberStepper("درد", pain, 0..10, "از ۱۰") { pain = it }
                if (pain > 0) TextAnswer("کجای بدن؟", painWhere, { painWhere = it }, maxLength = 240)
                DecimalField("وزن امروز (اختیاری)", weight, "کیلوگرم", { weight = it })
                val canSave = pain == 0 || painWhere.isNotBlank()
                Button(
                    onClick = {
                        state.saveCheckin(
                            DailyCheckin(
                                energyScore = energy, hungerScore = hunger, moodScore = mood, recoveryScore = recovery,
                                sleepMinutes = sleepHours * 60, painScore = pain,
                                painLocation = painWhere.trim().ifBlank { null },
                                weightKg = weight.toDoubleOrNull()?.takeIf { it in 20.0..500.0 },
                            ),
                            onDismiss,
                        )
                    },
                    enabled = canSave && !state.savingCheckin,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    if (state.savingCheckin) CircularProgressIndicator(strokeWidth = 2.dp) else Text("ثبت گزارش")
                }
                Text("گزارش روزانه برنامه این ماه را تغییر نمی‌دهد؛ در ساخت ماه بعد استفاده می‌شود.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
