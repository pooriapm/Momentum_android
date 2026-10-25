package com.pooriapm.momentum.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.data.Ingredient
import com.pooriapm.momentum.data.Meal
import com.pooriapm.momentum.ui.components.NumberStepper
import kotlinx.serialization.Serializable

/**
 * Shared table (سفرهٔ مشترک): the user cooks one of their plan meals for the household. The amount
 * is counted in shares the size of the user's own portion, so every number comes from the plan's
 * own ingredient list (already scaled to the user); no recipe yield is assumed.
 *
 *   total(i)    = personal(i) × portions
 *   shopping(i) = personal(i) × (portions − 1) more on that day than the personal plan
 *
 * The user's portion, calories and the meal's eaten/skipped status never change. It is a cooking
 * plan saved on this phone for one plan version; it changes the shopping list only.
 */
@Serializable
data class SharedCook(
    val date: String,
    val slotKey: String,
    val optionKey: String,
    val portions: Int,
)

const val SHARED_COOK_MIN = 2
const val SHARED_COOK_MAX = 8

/** Units the shopping list already sums; anything else cannot be scaled honestly. */
private val SCALABLE_UNITS = setOf("g", "ml", "piece", "tbsp", "tsp", "cup", "slice", "serving")

/** Why a new shared cook cannot be planned for this meal, or null when it can. */
fun sharedCookBlocker(meal: Meal, date: String, today: String): String? {
    val option = meal.chosenOption ?: return "برای این وعده غذایی انتخاب نشده است."
    return when {
        option.ingredients.isEmpty() -> "فهرست مواد این غذا در برنامه نیست؛ مقدار پخت را نمی‌توان حساب کرد."
        option.ingredients.size < 2 -> "این غذا یک قلم است و پختی ندارد."
        option.ingredients.any { it.unit.lowercase() !in SCALABLE_UNITS || it.amount <= 0.0 } ->
            "واحد بعضی از مواد این غذا قابل محاسبه نیست."
        date < today -> "این روز گذشته است."
        meal.completionStatus != "planned" -> "این وعده ثبت شده است. پخت مشترک برای وعده‌های پیش رو است."
        else -> null
    }
}

/** The shared cook that applies to [meal] now: same day, slot and chosen option. */
fun List<SharedCook>.activeFor(meal: Meal, date: String): SharedCook? =
    firstOrNull { it.date == date && it.slotKey == meal.slotKey && it.optionKey == meal.chosenOption?.optionKey }

/** A saved cook whose meal now has a different food: it no longer applies to shopping. */
fun List<SharedCook>.staleFor(meal: Meal, date: String): SharedCook? =
    firstOrNull { it.date == date && it.slotKey == meal.slotKey && it.optionKey != meal.chosenOption?.optionKey }

private fun amountLabel(amount: Double, unit: String) = shoppingAmount(amount, unit.lowercase())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedCookSheet(state: AppState, meal: Meal, date: String, today: String, onDismiss: () -> Unit, onDone: () -> Unit = onDismiss) {
    val option = meal.chosenOption ?: return
    val cooks = state.sharedCooks
    val current = cooks.activeFor(meal, date)
    val stale = cooks.staleFor(meal, date)
    val blocker = sharedCookBlocker(meal, date, today)
    var portions by rememberSaveable(meal.slotKey, date) { mutableIntStateOf(current?.portions ?: 4) }
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("سفرهٔ مشترک", style = MaterialTheme.typography.headlineSmall)
                Text("${meal.title} · ${jalaliLabel(date)} · ${option.title}", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }

            if (stale != null) {
                Notice("غذای این وعده عوض شده است؛ پخت مشترکِ قبلی (${stale.portions.fa()} سهم) دیگر در فهرست خرید حساب نمی‌شود.")
                OutlinedButton(onClick = { state.removeSharedCook(stale) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("حذف پخت قبلی")
                }
            }

            // Not supported (or no longer editable): say why and keep what was saved readable.
            if (blocker != null && current == null) {
                Notice(blocker)
                Text("می‌توانید سهم خودتان را مطابق برنامه بپزید.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("بستن") }
                return@Column
            }
            val editable = blocker == null

            Row(verticalAlignment = Alignment.CenterVertically) {
                ShareRing(portions, Modifier.size(96.dp))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("کل پخت: ${portions.fa()} سهم", style = MaterialTheme.typography.titleMedium)
                    Text("هر سهم هم‌اندازهٔ سهم شما در برنامه است. سهم شما و کالری برنامه ثابت می‌ماند.",
                        style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
            if (editable) {
                NumberStepper("مقدار کل پخت", portions, SHARED_COOK_MIN..SHARED_COOK_MAX, "سهم") { portions = it }
            } else {
                Notice(blocker!!)
            }

            HorizontalDivider()
            Text("مواد", style = MaterialTheme.typography.titleMedium)
            Row {
                Text("ماده", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1.4f))
                Text("کل پخت", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("سهم شما", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
            option.ingredients.forEach { IngredientRow(it, portions) }

            HorizontalDivider()
            ShoppingImpact(option.ingredients, before = current?.portions ?: 1, after = portions, date = date)

            Text("سهم دیگران از این فهرست تعیین نمی‌شود. حساسیت غذایی اعضای خانه را خودتان بررسی کنید. ثبت پخت، وعده را «خورده‌شده» ثبت نمی‌کند.",
                style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)

            if (editable) {
                Button(
                    onClick = { state.saveSharedCook(SharedCook(date, meal.slotKey, option.optionKey, portions)); onDone() },
                    enabled = portions != current?.portions,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text(if (current == null) "تایید پخت مشترک" else "ذخیرهٔ تغییر") }
                if (current != null) {
                    OutlinedButton(onClick = { state.removeSharedCook(current); onDone() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("لغو پخت مشترک")
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (editable) "انصراف" else "بستن") }
        }
    }
}

@Composable
private fun IngredientRow(ingredient: Ingredient, portions: Int) {
    val unit = ingredient.unit
    Row(
        Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
            contentDescription = "${ingredient.name}: کل پخت ${amountLabel(ingredient.amount * portions, unit)}، سهم شما ${amountLabel(ingredient.amount, unit)}"
        },
    ) {
        Text(ingredient.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.4f))
        Text(amountLabel(ingredient.amount * portions, unit), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(amountLabel(ingredient.amount, unit), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
    }
}

/** What confirming changes on the shopping list, compared with what is saved now. */
@Composable
private fun ShoppingImpact(ingredients: List<Ingredient>, before: Int, after: Int, date: String) {
    val colors = MaterialTheme.colorScheme
    val delta = after - before
    Text("اثر بر فهرست خرید", style = MaterialTheme.typography.titleMedium)
    when {
        delta == 0 -> Text("تغییری در فهرست خرید ایجاد نمی‌شود.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        else -> {
            Text(
                if (delta > 0) "برای ${jalaliLabel(date)} به خرید اضافه می‌شود:" else "از خرید ${jalaliLabel(date)} کم می‌شود:",
                style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant,
            )
            ingredients.forEach {
                Text("${if (delta > 0) "+" else "−"} ${amountLabel(it.amount * kotlin.math.abs(delta), it.unit)} ${it.name}",
                    style = MaterialTheme.typography.bodyMedium)
            }
            Text("خریدهای ثبت‌شده تغییر نمی‌کنند.", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun Notice(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(12.dp),
    )
}

/** The cook split into [portions] equal shares, the user's one highlighted. Shares, not progress. */
@Composable
private fun ShareRing(portions: Int, modifier: Modifier = Modifier) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val mine = MaterialTheme.colorScheme.tertiary
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {
            val stroke = 12.dp.toPx()
            val gapDeg = 6f
            val sweep = 360f / portions
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            repeat(portions) { i ->
                drawArc(
                    color = if (i == 0) mine else track,
                    startAngle = -90f + i * sweep + gapDeg / 2, sweepAngle = sweep - gapDeg, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke),
                )
            }
        }
        Text("۱ از ${portions.fa()}", style = MaterialTheme.typography.labelLarge)
    }
}
