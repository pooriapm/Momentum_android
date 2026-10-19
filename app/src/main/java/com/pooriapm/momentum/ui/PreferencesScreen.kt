package com.pooriapm.momentum.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.pooriapm.momentum.data.ApiException
import com.pooriapm.momentum.data.asApi
import com.pooriapm.momentum.ui.components.DecimalField
import com.pooriapm.momentum.ui.components.ErrorState
import com.pooriapm.momentum.ui.components.MomentumField
import com.pooriapm.momentum.ui.components.SegmentedPicker
import com.pooriapm.momentum.ui.theme.LocalGlow
import com.pooriapm.momentum.ui.theme.hueBackground
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

private val Goals = listOf("fat_loss" to "کاهش چربی", "muscle_gain" to "عضله‌سازی", "maintenance" to "حفظ وزن")
private val Diets = listOf("omnivore" to "همه‌چیزخوار", "vegetarian" to "گیاه‌خوار")
private val Allergens = listOf(
    "milk" to "شیر", "egg" to "تخم‌مرغ", "peanut" to "بادام‌زمینی", "tree_nut" to "مغزها",
    "wheat" to "گندم / گلوتن", "soy" to "سویا", "fish" to "ماهی", "shellfish" to "صدف و میگو", "sesame" to "کنجد",
)
// Saturday first; values match JavaScript getDay(), as the server expects.
private val Weekdays = listOf(6 to "ش", 0 to "ی", 1 to "د", 2 to "س", 3 to "چ", 4 to "پ", 5 to "ج")

/** The editable part of the settings, kept as plain values while the user edits. */
private data class Prefs(
    val goalType: String,
    val targetWeight: String,
    val diet: String,
    val favorites: String,
    val allergies: Set<String>,
    val otherAllergies: List<String>,
    val trainingDays: Set<Int>,
    val trainingTime: String,
    val trainingMinutes: Int,
)

private fun JsonObject.obj(key: String) = this[key] as? JsonObject
private fun JsonElement?.str() = (this as? JsonPrimitive)?.contentOrNull
private fun JsonElement?.list() = (this as? JsonArray)?.mapNotNull { it.str() }.orEmpty()

private fun readPrefs(settings: JsonObject): Prefs {
    val goal = settings.obj("goal")
    val dietary = settings.obj("dietary")
    val schedule = (settings["schedule"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val allergies = dietary?.get("allergies").list()
    val known = Allergens.map { it.first }.toSet()
    return Prefs(
        goalType = goal?.get("goal_type").str() ?: "maintenance",
        targetWeight = goal?.get("target_weight_kg")?.jsonPrimitive?.doubleOrNull?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }.orEmpty(),
        diet = dietary?.get("dietary_pattern").str() ?: "omnivore",
        favorites = dietary?.get("favorite_foods").list().joinToString("، "),
        allergies = allergies.filter { it in known }.toSet(),
        otherAllergies = allergies.filterNot { it in known },
        trainingDays = schedule.mapNotNull { it["weekday"]?.jsonPrimitive?.intOrNull }.toSet(),
        trainingTime = schedule.firstOrNull()?.get("local_start_time").str()?.take(5) ?: "18:00",
        trainingMinutes = schedule.firstOrNull()?.get("duration_minutes")?.jsonPrimitive?.intOrNull ?: 45,
    )
}

/**
 * The server replaces the whole settings document on update, so the payload carries every editable
 * field: the unchanged ones from what was loaded, and goal, foods and schedule from the form.
 */
private fun buildPayload(settings: JsonObject, p: Prefs): JsonObject {
    val profile = settings.obj("profile")
    val goal = settings.obj("goal")
    val dietary = settings.obj("dietary")
    val oldSchedule = (settings["schedule"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    val activity = oldSchedule.firstOrNull()?.get("activity_type").str() ?: "full_body"
    fun list(values: List<String>) = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }
    return buildJsonObject {
        put("displayName", profile?.get("display_name").str().orEmpty())
        put("sex", profile?.get("sex").str()?.let { if (it == "undisclosed") "prefer_not_to_say" else it } ?: "prefer_not_to_say")
        profile?.get("height_cm")?.jsonPrimitive?.doubleOrNull?.let { put("heightCm", it) }
        put("locale", profile?.get("locale").str() ?: "fa-IR")
        put("unitSystem", profile?.get("unit_system").str() ?: "metric")
        put("goalType", p.goalType)
        goal?.get("custom_goal").str()?.let { put("customGoal", it) }
        val target = p.targetWeight.toDoubleOrNull() ?: goal?.get("start_weight_kg")?.jsonPrimitive?.doubleOrNull
        if (target != null) put("targetWeightKg", target)
        put("dietaryPattern", p.diet)
        put("favoriteFoods", list(p.favorites.split(',', '،', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(50)))
        put("allergies", list((p.allergies.toList() + p.otherAllergies).distinct()))
        put("availableEquipment", dietary?.get("available_equipment") ?: JsonArray(emptyList()))
        put("workSchedule", dietary?.get("work_schedule").str().orEmpty())
        put("cuisineRegion", dietary?.get("cuisine_region").str() ?: "iran")
        put("schedule", buildJsonArray {
            p.trainingDays.sorted().forEach { day ->
                add(buildJsonObject {
                    put("weekday", day)
                    put("activityType", activity)
                    put("localStartTime", p.trainingTime)
                    put("durationMinutes", p.trainingMinutes)
                })
            }
        })
    }
}

/**
 * Edit goal, food and training schedule after onboarding (account-settings). Changes are saved for
 * the next monthly plan; the current month stays as it is, which the screen says up front.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreferencesScreen(state: AppState, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BackHandler(onBack = onClose)
        val dark = isSystemInDarkTheme()
        val view = LocalView.current
        LaunchedEffect(dark) {
            (view.parent as? DialogWindowProvider)?.window?.let { WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = !dark }
        }
        val scope = rememberCoroutineScope()
        var loaded by remember { mutableStateOf<JsonObject?>(null) }
        var prefs by remember { mutableStateOf<Prefs?>(null) }
        var error by remember { mutableStateOf<ApiException?>(null) }
        var saving by remember { mutableStateOf(false) }
        var reload by remember { mutableIntStateOf(0) }
        LaunchedEffect(reload) {
            error = null
            try {
                val settings = state.api.accountSettings()
                loaded = settings
                prefs = readPrefs(settings)
            } catch (raw: Exception) {
                error = raw.asApi()
            }
        }

        Box(Modifier.fillMaxSize().hueBackground(LocalGlow.current).safeDrawingPadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "بستن") }
                    Text("ترجیحات برنامه", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
                    GuideButton(Guides.preferences, Modifier.padding(end = 12.dp))
                }
                val p = prefs
                when {
                    error != null && p == null -> ErrorState(error!!, onRetry = { reload++ })
                    p == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    else -> Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Text(
                                "تغییرها برای برنامه ماه بعد ذخیره می‌شود؛ برنامه این ماه همین‌طور می‌ماند.",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(14.dp),
                            )
                        }
                        Section("هدف") {
                            SegmentedPicker(Goals.map { it.second }, Goals.indexOfFirst { it.first == p.goalType }.takeIf { it >= 0 }) {
                                prefs = p.copy(goalType = Goals[it].first)
                            }
                            if (p.goalType != "maintenance") {
                                DecimalField("وزن هدف", p.targetWeight, "کیلوگرم", { prefs = p.copy(targetWeight = it) })
                            }
                        }
                        Section("غذا") {
                            SegmentedPicker(Diets.map { it.second }, Diets.indexOfFirst { it.first == p.diet }.takeIf { it >= 0 }) {
                                prefs = p.copy(diet = Diets[it].first)
                            }
                            MomentumField(
                                value = p.favorites,
                                onValueChange = { if (it.length <= 2000) prefs = p.copy(favorites = it) },
                                label = "غذاهایی که دوست داری",
                                placeholder = "مثلاً قورمه‌سبزی، مرغ، ماست",
                                singleLine = false,
                                minLines = 2,
                            )
                            Text("حساسیت غذایی", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Allergens.forEach { (key, label) ->
                                    FilterChip(
                                        selected = key in p.allergies,
                                        onClick = { prefs = p.copy(allergies = if (key in p.allergies) p.allergies - key else p.allergies + key) },
                                        label = { Text(label) },
                                    )
                                }
                            }
                            if (p.otherAllergies.isNotEmpty()) {
                                Text("سایر: ${p.otherAllergies.joinToString("، ")}", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Section("برنامه تمرین") {
                            Text("روزهای تمرین", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                Weekdays.forEach { (day, label) ->
                                    val on = day in p.trainingDays
                                    Box(
                                        Modifier
                                            .weight(1f)
                                            .heightIn(min = 44.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable(role = Role.Checkbox) {
                                                prefs = p.copy(trainingDays = if (on) p.trainingDays - day else p.trainingDays + day)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(label, style = MaterialTheme.typography.titleSmall,
                                            color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                            if (p.trainingDays.isNotEmpty()) {
                                Text("مدت هر جلسه", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val durations = listOf(20, 30, 45, 60, 90)
                                SegmentedPicker(durations.map { "${it.fa()}′" }, durations.indexOf(p.trainingMinutes).takeIf { it >= 0 }) {
                                    prefs = p.copy(trainingMinutes = durations[it])
                                }
                                Text("ساعت تمرین", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                val times = listOf("07:00", "12:00", "17:00", "18:00", "20:00")
                                SegmentedPicker(times.map { it.fa() }, times.indexOf(p.trainingTime).takeIf { it >= 0 }) {
                                    prefs = p.copy(trainingTime = times[it])
                                }
                            }
                        }
                        Spacer(Modifier.heightIn(min = 8.dp))
                    }
                }
                if (p != null) {
                    val targetOk = p.goalType == "maintenance" || p.targetWeight.toDoubleOrNull()?.let { it in 35.0..350.0 } == true
                    Button(
                        onClick = {
                            val settings = loaded ?: return@Button
                            saving = true
                            scope.launch {
                                try {
                                    state.api.updateAccountSettings(buildPayload(settings, p))
                                    state.message = UiMessage("ترجیحاتت ذخیره شد؛ برنامه ماه بعد بر اساس آن ساخته می‌شود.", isError = false)
                                    onClose()
                                } catch (raw: Exception) {
                                    state.message = UiMessage(raw.asApi().message.orEmpty())
                                } finally {
                                    saving = false
                                }
                            }
                        },
                        enabled = !saving && targetOk,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 52.dp),
                    ) {
                        if (saving) CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp)) else Text("ذخیره")
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
