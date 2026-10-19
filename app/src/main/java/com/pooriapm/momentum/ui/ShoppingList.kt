package com.pooriapm.momentum.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.pooriapm.momentum.data.Plan
import com.pooriapm.momentum.ui.components.LiquidSelector
import com.pooriapm.momentum.ui.theme.LocalGlow
import com.pooriapm.momentum.ui.theme.hueBackground

/** Bigger units once amounts get large: 1250 g → 1.3 kg, 1500 ml → 1.5 liters. */
fun shoppingAmount(amount: Double, unit: String): String = when {
    unit == "g" && amount >= 1000 -> "${(amount / 1000).faDecimal()} کیلوگرم"
    unit == "ml" && amount >= 1000 -> "${(amount / 1000).faDecimal()} لیتر"
    else -> "${amount.faDecimal()} ${unitLabel(unit)}"
}

/** The single, quiet entry point on the Plan tab: what is left for this shopping week. */
@Composable
fun ShoppingEntry(state: AppState, plan: Plan, today: String) {
    LaunchedEffect(plan.versionId, plan.validFrom) { state.loadShopping() }
    var open by rememberSaveable { mutableStateOf(false) }
    val purchases = state.shopping
    val week = purchases?.let { weekWindow(plan, today, it) }
    val lines = if (purchases != null && week != null) shoppingLines(plan, today, week, purchases) else emptyList()
    val left = lines.count { !it.covered }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) {
            if (purchases == null) state.loadShopping(force = true) else open = true
        },
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.ShoppingBasket, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("فهرست خرید", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        purchases == null && state.shoppingError != null -> "${state.shoppingError?.message} · برای تلاش دوباره بزن"
                        purchases == null -> "در حال بارگذاری…"
                        lines.isEmpty() -> "برای روزهای پیش رو خریدی لازم نیست"
                        left == 0 -> "خرید این هفته کامل است ✓"
                        else -> "${left.fa()} قلم تا ${jalaliLabel(week!!.end)}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (purchases == null && state.shoppingError == null) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (open && purchases != null) ShoppingSheet(state, plan, today) { open = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShoppingSheet(state: AppState, plan: Plan, today: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val glow = LocalGlow.current
    val backdrop = rememberLayerBackdrop()
    var scopeIndex by rememberSaveable { mutableIntStateOf(0) }
    val scope = if (scopeIndex == 0) ShoppingScope.Week else ShoppingScope.Plan
    val purchases = state.shopping.orEmpty()
    val window = if (scope == ShoppingScope.Week) weekWindow(plan, today, purchases) else planWindow(plan, today)
    val lines = shoppingLines(plan, today, window, purchases)
    val toBuy = lines.filterNot { it.covered }
    val covered = lines.filter { it.covered }
    var showCovered by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = glow.base,
        dragHandle = null,
    ) {
GuideHost(Modifier.fillMaxWidth()) {
            GuideOnFirstVisit(Guides.shopping)
            Box(Modifier.fillMaxWidth()) {
                Box(Modifier.matchParentSize().glassSource(backdrop).hueBackground(glow))
                Column(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { BottomSheetDefaults.DragHandle() }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("فهرست خرید", style = MaterialTheme.typography.headlineSmall)
                                Text(
                                    "${jalaliLabel(window.start.coerceAtLeast(today))} تا ${jalaliLabel(window.end)} · برای یک نفر",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            GuideInfoButton(Guides.shopping)
                            IconButton(onClick = { share(context, toBuy) }, enabled = toBuy.isNotEmpty(), modifier = Modifier.guideTarget("shop_share")) {
                                Icon(Icons.Rounded.Share, contentDescription = "اشتراک‌گذاری فهرست", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        LiquidSelector(count = 2, selected = scopeIndex, onSelect = { scopeIndex = it }, backdrop = backdrop, height = 48.dp, modifier = Modifier.guideTarget("shop_scope")) { i ->
                            Text(if (i == 0) "هفته خرید" else "تا پایان برنامه", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text(
                            when {
                                lines.isEmpty() -> "برای این بازه خریدی لازم نیست."
                                toBuy.isEmpty() -> "همه چیز این بازه را داری ✓"
                                scope == ShoppingScope.Week -> "${toBuy.size.fa()} قلم مانده · با تیک زدن، همان مقدار از خرید ماه هم کم می‌شود."
                                else -> "${toBuy.size.fa()} قلم مانده · خرید هفتگی‌ات از این مقدارها کم شده است."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 520.dp).guideTarget("shop_list"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        items(toBuy, key = { "buy-" + it.key }) { line ->
                            Column(Modifier.animateItem()) {
                                ShoppingRow(line, checked = false, busy = line.key in state.shoppingBusy) { state.buyShopping(line, scope) }
                                HorizontalDivider(Modifier.padding(start = 52.dp, end = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        if (covered.isNotEmpty()) {
                            item(key = "covered-header") {
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { showCovered = !showCovered }.padding(horizontal = 8.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("خریده‌شده (${covered.size.fa()})", style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                    Icon(if (showCovered) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            items(covered, key = { "covered-" + it.key }) { line ->
                                AnimatedVisibility(showCovered, modifier = Modifier.animateItem()) {
                                    ShoppingRow(line, checked = true, busy = line.key in state.shoppingBusy) { state.unbuyShopping(line) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.navigationBarsPadding().height(12.dp))
                }
}
        }
    }
}

@Composable
private fun ShoppingRow(line: ShoppingLine, checked: Boolean, busy: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val fill by animateColorAsState(if (checked) colors.tertiary else colors.surface, label = "check-fill")
    val alpha by animateFloatAsState(if (checked) 0.55f else 1f, label = "row-alpha")
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = !busy, role = Role.Checkbox, onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(fill).border(1.5.dp, if (checked) colors.tertiary else colors.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                busy -> CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                checked -> Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.onTertiary, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).graphicsLayer { this.alpha = alpha }) {
            Text(line.name, style = MaterialTheme.typography.bodyLarge, textDecoration = if (checked) TextDecoration.LineThrough else null)
            line.coveredUntil?.let {
                Text(if (checked) "کافی تا ${jalaliLabel(it)}" else "موجودی تا ${jalaliLabel(it)}",
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
        Text(
            shoppingAmount(if (checked) line.needInWindow else line.toBuy, line.unit),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            modifier = Modifier.graphicsLayer { this.alpha = alpha },
        )
    }
}

private fun share(context: Context, lines: List<ShoppingLine>) {
    val text = buildString {
        append("فهرست خرید Momentum\n")
        lines.forEach { append("• ${it.name}: ${shoppingAmount(it.toBuy, it.unit)}\n") }
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, "اشتراک‌گذاری فهرست خرید")) }
}
