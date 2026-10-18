package com.pooriapm.momentum.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.ui.fa
import com.pooriapm.momentum.ui.jalaliMonthLength
import com.pooriapm.momentum.ui.jalaliMonths

/**
 * One question screen: progress on top, scrollable content, one primary action pinned to the
 * bottom (thumb zone), back arrow for the previous answer. Answers are kept when going back.
 */
@Composable
fun StepScaffold(
    progress: Float,
    stepLabel: String,
    title: String,
    subtitle: String?,
    onBack: (() -> Unit)?,
    primaryLabel: String,
    primaryEnabled: Boolean,
    loading: Boolean = false,
    error: String? = null,
    onPrimary: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val animated by animateFloatAsState(progress, label = "progress")
    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    // Auto-mirrored: in RTL the back arrow points right (→), toward where the user came from.
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "قبلی")
                }
            } else {
                Spacer(Modifier.size(48.dp))
            }
            LinearProgressIndicator(
                progress = { animated },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).height(6.dp)
                    .semantics { stateDescription = stepLabel },
            )
            Text(stepLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp))
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Spacer(Modifier.height(4.dp))
            content()
        }
        Surface(tonalElevation = 2.dp) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                Button(
                    onClick = onPrimary,
                    enabled = primaryEnabled && !loading,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(primaryLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** Large single-choice card (radio semantics for TalkBack). Wrap a set in [ChoiceGroup]. */
@Composable
fun ChoiceCard(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    val border by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "border",
    )
    OutlinedCard(
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(14.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (selected) Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ChoiceGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
}

/** Multi-select chips; [selected] are option values. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(options: List<Pair<String, String>>, selected: Set<String>, onToggle: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value in selected,
                onClick = { onToggle(value) },
                label = { Text(label) },
                modifier = Modifier.heightIn(min = 48.dp),
            )
        }
    }
}

/** Yes/no answer as a segmented control. */
@Composable
fun YesNoQuestion(question: String, value: String?, onChange: (String) -> Unit) {
    val keys = listOf("no", "yes")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(question, style = MaterialTheme.typography.bodyLarge)
        SegmentedPicker(listOf("خیر", "بله"), keys.indexOf(value).takeIf { it >= 0 }) { onChange(keys[it]) }
    }
}

/**
 * iOS-style segmented control: a capsule track with a thumb that slides to the chosen option.
 * Built on `offset`, which mirrors with the layout, so the first option sits on the right in
 * Persian and the rounded ends are always the outer ones (M3 segmented buttons get them wrong in RTL).
 */
@Composable
fun SegmentedPicker(options: List<String>, selectedIndex: Int?, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(colors.surfaceVariant)
            .padding(3.dp),
    ) {
        val segment = maxWidth / options.size
        if (selectedIndex != null) {
            val x by animateDpAsState(segment * selectedIndex, spring(dampingRatio = 0.8f, stiffness = 500f), label = "thumb")
            Box(
                Modifier
                    .offset(x = x)
                    .width(segment)
                    .fillMaxHeight()
                    .shadow(2.dp, CircleShape)
                    .background(colors.surface, CircleShape),
            )
        }
        Row(Modifier.fillMaxSize().selectableGroup()) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val tint by animateColorAsState(if (selected) colors.primary else colors.onSurfaceVariant, label = "label")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .selectable(selected = selected, role = Role.RadioButton) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        color = tint,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** Integer stepper with large tap targets; announces the value with its unit. */
@Composable
fun NumberStepper(label: String, value: Int, range: IntRange, unit: String = "", onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().semantics { contentDescription = "$label: ${value.fa()} $unit" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        FilledTonalIconButton(onClick = { onChange(value - 1) }, enabled = value > range.first) {
            Icon(Icons.Outlined.Remove, contentDescription = "کم کردن")
        }
        Text("${value.fa()} $unit".trim(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 12.dp))
        FilledTonalIconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
            Icon(Icons.Outlined.Add, contentDescription = "زیاد کردن")
        }
    }
}

/** Decimal input such as weight; accepts Persian or Latin digits and stores Latin. */
@Composable
fun DecimalField(label: String, value: String, unit: String, onChange: (String) -> Unit, supporting: String? = null) {
    MomentumField(
        value = value.fa().replace('.', '٫'),
        onValueChange = { raw ->
            val latin = raw.map { c -> if (c in '۰'..'۹') '0' + (c - '۰') else if (c == '٫' || c == ',') '.' else c }
                .filter { it.isDigit() || it == '.' }.joinToString("")
            if (latin.count { it == '.' } <= 1 && latin.length <= 6) onChange(latin)
        },
        label = label,
        placeholder = "—",
        suffix = unit,
        supporting = supporting,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun TextAnswer(label: String, value: String, onChange: (String) -> Unit, hint: String? = null, maxLength: Int = 1000, minLines: Int = 1) {
    MomentumField(
        value = value,
        onValueChange = { if (it.length <= maxLength) onChange(it) },
        label = label,
        placeholder = hint,
        singleLine = minLines <= 1,
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

/**
 * Jalali date as three dropdowns (day, month, year). The M3 date picker only knows the
 * Gregorian calendar; Iranian users read and remember birth dates in Jalali.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JalaliDateField(year: Int?, month: Int?, day: Int?, years: IntProgression, onChange: (Int?, Int?, Int?) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val maxDay = if (year != null && month != null) jalaliMonthLength(year, month) else 31
        Dropdown("روز", day?.fa(), (1..maxDay).map { it to it.fa() }, Modifier.weight(1f)) { onChange(year, month, it) }
        Dropdown("ماه", month?.let { jalaliMonths[it - 1] }, (1..12).map { it to jalaliMonths[it - 1] }, Modifier.weight(1.4f)) {
            onChange(year, it, day?.coerceAtMost(if (year != null) jalaliMonthLength(year, it) else 31))
        }
        Dropdown("سال", year?.fa(), years.map { it to it.fa() }, Modifier.weight(1.2f)) {
            onChange(it, month, day?.let { d -> if (month != null) d.coerceAtMost(jalaliMonthLength(it, month)) else d })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Dropdown(label: String, shown: String?, options: List<Pair<T, String>>, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        MomentumField(
            value = shown.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = label,
            placeholder = "انتخاب",
            trailing = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

/** Card-like information block used on review and summary screens. */
@Composable
fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

/**
 * Full-screen error with a clear title per kind, the server's explanation, a retry when it can
 * help, and an optional secondary action (e.g. sign out when the account has no access).
 */
@Composable
fun ErrorState(
    error: com.pooriapm.momentum.data.ApiException,
    onRetry: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val (icon, title) = when (error.kind) {
        com.pooriapm.momentum.data.ErrorKind.Offline -> Icons.Outlined.CloudOff to "به اینترنت وصل نیستی"
        com.pooriapm.momentum.data.ErrorKind.Timeout -> Icons.Outlined.HourglassEmpty to "پاسخ دیر رسید"
        com.pooriapm.momentum.data.ErrorKind.NoAccess -> Icons.Outlined.Lock to "دسترسی هنوز فعال نیست"
        com.pooriapm.momentum.data.ErrorKind.NotConfigured -> Icons.Outlined.Settings to "اپ تنظیم نشده"
        else -> Icons.Outlined.ErrorOutline to "مشکلی پیش آمد"
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Text(error.message.orEmpty(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        if (error.retryable || error.kind == com.pooriapm.momentum.data.ErrorKind.NoAccess) {
            Button(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("تلاش دوباره") }
        }
        if (secondaryLabel != null && onSecondary != null) {
            androidx.compose.material3.TextButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}

/** Thin banner pinned to the top while there is no internet; writes are blocked until it returns. */
@Composable
fun OfflineBanner(online: Boolean) {
    androidx.compose.animation.AnimatedVisibility(visible = !online) {
        Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("آفلاین هستی؛ برای ثبت تغییرها دوباره به اینترنت وصل شو.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
