package com.pooriapm.momentum.ui.components

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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Add
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
                    // ArrowForward is auto-mirrored, so in RTL it points right = "back".
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = "قبلی")
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

/** Yes/no answer as an M3 segmented button. */
@Composable
fun YesNoQuestion(question: String, value: String?, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(question, style = MaterialTheme.typography.bodyLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("no" to "خیر", "yes" to "بله").forEachIndexed { index, (key, label) ->
                SegmentedButton(
                    selected = value == key,
                    onClick = { onChange(key) },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                ) { Text(label) }
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
        Text("${value.fa()} $unit".trim(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 12.dp))
        FilledTonalIconButton(onClick = { onChange(value + 1) }, enabled = value < range.last) {
            Icon(Icons.Outlined.Add, contentDescription = "زیاد کردن")
        }
    }
}

/** Decimal input such as weight; accepts Persian or Latin digits and stores Latin. */
@Composable
fun DecimalField(label: String, value: String, unit: String, onChange: (String) -> Unit, supporting: String? = null) {
    OutlinedTextField(
        value = value.fa(),
        onValueChange = { raw ->
            val latin = raw.map { c -> if (c in '۰'..'۹') '0' + (c - '۰') else if (c == '٫' || c == ',') '.' else c }
                .filter { it.isDigit() || it == '.' }.joinToString("")
            if (latin.count { it == '.' } <= 1 && latin.length <= 6) onChange(latin)
        },
        label = { Text(label) },
        suffix = { Text(unit) },
        supportingText = supporting?.let { { Text(it) } },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun TextAnswer(label: String, value: String, onChange: (String) -> Unit, hint: String? = null, maxLength: Int = 1000, minLines: Int = 1) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.length <= maxLength) onChange(it) },
        label = { Text(label) },
        placeholder = hint?.let { { Text(it) } },
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
        OutlinedTextField(
            value = shown.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            singleLine = true,
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
