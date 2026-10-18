package com.pooriapm.momentum.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.data.Plan
import com.pooriapm.momentum.ui.components.SegmentedPicker

/** One line of the list: an ingredient and its total for the chosen period. */
data class ShoppingItem(val key: String, val name: String, val amount: Double, val unit: String)

/**
 * Totals the ingredients of the meals the user will actually eat: the chosen (or default) option
 * of every meal from [fromDate] for [days] days (all remaining days when null). Skipped and eaten
 * meals of today still count; the list is for buying, not tracking.
 */
fun buildShoppingList(plan: Plan, fromDate: String, days: Int?): List<ShoppingItem> {
    val upcoming = plan.days.filter { it.localDate >= fromDate }.sortedBy { it.localDate }
    val window = if (days == null) upcoming else upcoming.take(days)
    val totals = LinkedHashMap<String, ShoppingItem>()
    window.forEach { day ->
        day.meals.forEach { meal ->
            meal.chosenOption?.ingredients?.forEach { ingredient ->
                val unit = ingredient.unit.lowercase()
                val key = "${ingredient.name.trim()}|$unit"
                val previous = totals[key]
                totals[key] = ShoppingItem(key, ingredient.name.trim(), (previous?.amount ?: 0.0) + ingredient.amount, unit)
            }
        }
    }
    return totals.values.sortedBy { it.name }
}

/** Bigger units once amounts get large: 1250 g → 1.3 kg, 1500 ml → 1.5 liters. */
fun shoppingAmount(item: ShoppingItem): String = when {
    item.unit == "g" && item.amount >= 1000 -> "${(item.amount / 1000).faDecimal()} کیلوگرم"
    item.unit == "ml" && item.amount >= 1000 -> "${(item.amount / 1000).faDecimal()} لیتر"
    else -> "${item.amount.faDecimal()} ${unitLabel(item.unit)}"
}

/** Ticks stay on this device (like the website) and only for the current plan. */
private class ShoppingChecks(context: Context) {
    private val prefs = context.getSharedPreferences("momentum.shopping", Context.MODE_PRIVATE)

    fun load(planKey: String): Set<String> =
        if (prefs.getString("plan", null) == planKey) prefs.getStringSet("checked", emptySet()).orEmpty().toSet() else emptySet()

    fun save(planKey: String, checked: Set<String>) {
        prefs.edit().putString("plan", planKey).putStringSet("checked", checked).apply()
    }
}

private fun planKey(plan: Plan) = "${plan.validFrom}:${plan.name}"

/** The single, quiet entry point on the Plan tab: count and progress, opens the list. */
@Composable
fun ShoppingEntry(plan: Plan, today: String) {
    val context = LocalContext.current
    val store = remember { ShoppingChecks(context) }
    var open by rememberSaveable { mutableStateOf(false) }
    var checked by remember(plan) { mutableStateOf(store.load(planKey(plan))) }
    val weekItems = remember(plan, today) { buildShoppingList(plan, today, 7) }
    if (weekItems.isEmpty()) return
    val bought = weekItems.count { it.key in checked }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { open = true },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.ShoppingBasket, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("فهرست خرید", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (bought == 0) "${weekItems.size.fa()} قلم برای ۷ روز آینده"
                    else "${bought.fa()} از ${weekItems.size.fa()} قلم خریده شد",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open) {
        ShoppingSheet(plan, today, checked, onToggle = { key ->
            checked = if (key in checked) checked - key else checked + key
            store.save(planKey(plan), checked)
        }, onClear = {
            checked = emptySet()
            store.save(planKey(plan), checked)
        }) { open = false }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingSheet(
    plan: Plan,
    today: String,
    checked: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var wholePlan by rememberSaveable { mutableStateOf(false) }
    val items = remember(plan, today, wholePlan) { buildShoppingList(plan, today, if (wholePlan) null else 7) }
    // Still-to-buy first; bought items sink to the bottom.
    val ordered = items.sortedBy { it.key in checked }
    val bought = items.count { it.key in checked }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("فهرست خرید", style = MaterialTheme.typography.headlineSmall)
                    Text("مقدارها برای یک نفر است", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { share(context, items) }) {
                    Icon(Icons.Rounded.Share, contentDescription = "اشتراک‌گذاری فهرست", tint = MaterialTheme.colorScheme.primary)
                }
            }
            SegmentedPicker(listOf("۷ روز آینده", "تا پایان برنامه"), if (wholePlan) 1 else 0) { wholePlan = it == 1 }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { if (items.isEmpty()) 0f else bought.toFloat() / items.size },
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier.weight(1f).height(6.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text("${bought.fa()} از ${items.size.fa()}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(
            Modifier.fillMaxWidth().heightIn(max = 520.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            items(ordered, key = { it.key }) { item ->
                Column(Modifier.animateItem()) {
                    ShoppingRow(item, item.key in checked) { onToggle(item.key) }
                    HorizontalDivider(Modifier.padding(start = 52.dp, end = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).navigationBarsPadding().padding(bottom = 8.dp)) {
            if (bought > 0) TextButton(onClick = onClear) { Text("پاک کردن تیک‌ها") }
        }
    }
}

@Composable
private fun ShoppingRow(item: ShoppingItem, checked: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (checked) colors.tertiary else colors.surface, label = "check-fill")
    val alpha by animateFloatAsState(if (checked) 0.5f else 1f, label = "row-alpha")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(fill).border(1.5.dp, if (checked) colors.tertiary else colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onTertiary, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            item.name,
            style = MaterialTheme.typography.bodyLarge,
            textDecoration = if (checked) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f).graphicsLayer { this.alpha = alpha },
        )
        Text(
            shoppingAmount(item),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { this.alpha = alpha },
        )
    }
}

private fun share(context: Context, items: List<ShoppingItem>) {
    val text = buildString {
        append("فهرست خرید Momentum\n")
        items.forEach { append("• ${it.name}: ${shoppingAmount(it)}\n") }
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, "اشتراک‌گذاری فهرست خرید")) }
}
