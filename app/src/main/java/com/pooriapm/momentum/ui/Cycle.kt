package com.pooriapm.momentum.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.BuildConfig
import com.pooriapm.momentum.data.CycleData
import com.pooriapm.momentum.data.CycleEntry
import com.pooriapm.momentum.data.CycleStore
import com.pooriapm.momentum.ui.components.JalaliDateField
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Optional cycle log (behind BuildConfig.CYCLE_PREVIEW, never on in release). Opens with its own
 * consent; afterwards the user logs period start/end dates, sees past intervals between logged
 * starts, exports them as text when they choose, and can delete everything. No predictions, no
 * reminders, nothing sent to the server, and nothing feeds the meal or training plan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CycleSheet(state: AppState, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { CycleStore(context) }
    val userId = state.api.session?.userId ?: return
    var data by remember { mutableStateOf(store.load(userId)) }
    var confirmDelete by remember { mutableStateOf(false) }
    fun save(next: CycleData) { store.save(userId, next); data = next }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("ثبت چرخه", style = MaterialTheme.typography.headlineSmall)
            val current = data
            if (current == null) {
                Consent(onAccept = { save(CycleData(CycleStore.CONSENT_VERSION, LocalDate.now().toString())) }, onDecline = onDismiss)
            } else {
                Text("فقط روی همین گوشی و رمزنگاری‌شده نگه داشته می‌شود.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AddEntry(current.entries) { entry -> save(current.copy(entries = (current.entries + entry).sortedByDescending { it.start })) }
                HorizontalDivider()
                Entries(current.entries) { gone -> save(current.copy(entries = current.entries - gone)) }
                HorizontalDivider()
                OutlinedButton(
                    onClick = { export(context, current.entries) },
                    enabled = current.entries.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) { Text("خروجی متنی تاریخ‌ها") }
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("حذف همه و لغو رضایت", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("حذف همهٔ تاریخ‌ها؟") },
            text = { Text("همهٔ ثبت‌های چرخه از این گوشی پاک می‌شود و برگشت‌پذیر نیست. برای استفادهٔ دوباره باید رضایت را دوباره بدهید.") },
            confirmButton = {
                TextButton(onClick = { store.delete(userId); data = null; confirmDelete = false }) {
                    Text("حذف", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("انصراف") } },
        )
    }
}

@Composable
private fun Consent(onAccept: () -> Unit, onDecline: () -> Unit) {
    var agreed by remember { mutableStateOf(false) }
    Text("این بخش اختیاری است و برنامهٔ غذا و تمرین شما را تغییر نمی‌دهد.", style = MaterialTheme.typography.bodyMedium)
    listOf(
        "تاریخ‌ها فقط روی همین گوشی و رمزنگاری‌شده ذخیره می‌شوند؛ به سرور، اعلان یا ویجت نمی‌روند.",
        "پیش‌بینی یا تشخیص انجام نمی‌شود؛ فقط آنچه ثبت می‌کنید نمایش داده می‌شود.",
        "با حذف اپ یا تعویض گوشی این تاریخ‌ها از بین می‌روند. هر وقت بخواهید می‌توانید همه را حذف کنید.",
    ).forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = agreed, onCheckedChange = { agreed = it })
        Text("این شرایط را خواندم و موافقم.", style = MaterialTheme.typography.bodyMedium)
    }
    Button(onClick = onAccept, enabled = agreed, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("فعال‌کردن ثبت چرخه") }
    TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("نه، بستن") }
}

@Composable
private fun AddEntry(existing: List<CycleEntry>, onAdd: (CycleEntry) -> Unit) {
    val today = todayJalali()
    var start by remember { mutableStateOf<Triple<Int?, Int?, Int?>>(Triple(today.first, today.second, today.third)) }
    var end by remember { mutableStateOf<Triple<Int?, Int?, Int?>>(Triple(null, null, null)) }
    val years = (today.first - 1)..today.first
    fun iso(t: Triple<Int?, Int?, Int?>) = if (t.first != null && t.second != null && t.third != null) jalaliToIso(t.first!!, t.second!!, t.third!!) else null
    val startIso = iso(start)
    val endIso = iso(end)
    val todayIso = LocalDate.now().toString()
    val error = when {
        startIso == null -> "تاریخ شروع کامل نیست."
        startIso > todayIso -> "تاریخ شروع نمی‌تواند بعد از امروز باشد."
        existing.any { it.start == startIso } -> "این تاریخ شروع قبلاً ثبت شده است."
        end.first != null && endIso == null -> "تاریخ پایان کامل نیست."
        endIso != null && (endIso < startIso || endIso > todayIso) -> "تاریخ پایان باید بین شروع و امروز باشد."
        else -> null
    }
    Text("ثبت تازه", style = MaterialTheme.typography.titleMedium)
    Text("شروع", style = MaterialTheme.typography.labelLarge)
    JalaliDateField(start.first, start.second, start.third, years) { y, m, d -> start = Triple(y, m, d) }
    Text("پایان (اختیاری)", style = MaterialTheme.typography.labelLarge)
    JalaliDateField(end.first, end.second, end.third, years) { y, m, d -> end = Triple(y, m, d) }
    error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
    Button(
        onClick = { onAdd(CycleEntry(startIso!!, endIso)); end = Triple(null, null, null) },
        enabled = error == null,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) { Text("ثبت") }
}

@Composable
private fun Entries(entries: List<CycleEntry>, onRemove: (CycleEntry) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text("ثبت‌ها", style = MaterialTheme.typography.titleMedium)
    if (entries.isEmpty()) {
        Text("هنوز تاریخی ثبت نشده است.", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        return
    }
    val sorted = entries.sortedByDescending { it.start }
    sorted.forEachIndexed { i, entry ->
        // Only the interval between two dates the user logged; nothing is projected forward.
        val gap = sorted.getOrNull(i + 1)?.let { ChronoUnit.DAYS.between(LocalDate.parse(it.start), LocalDate.parse(entry.start)).toInt() }
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${jalaliLabel(entry.start)}${entry.end?.let { " تا ${jalaliLabel(it)}" } ?: ""}", style = MaterialTheme.typography.bodyLarge)
                gap?.let { Text("${it.fa()} روز بعد از شروع ثبت‌شدهٔ قبلی", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant) }
            }
            TextButton(onClick = { onRemove(entry) }) { Text("حذف") }
        }
    }
}

/** Plain text through the system share sheet: the user picks where it goes, or cancels. */
private fun export(context: android.content.Context, entries: List<CycleEntry>) {
    val text = buildString {
        append("ثبت‌های چرخه (Momentum)\n")
        entries.sortedBy { it.start }.forEach { append("• ${jalaliLabel(it.start)}${it.end?.let { e -> " تا ${jalaliLabel(e)}" } ?: ""}\n") }
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, "خروجی ثبت‌های چرخه")) }
}

fun cycleEnabled() = BuildConfig.CYCLE_PREVIEW
