package com.pooriapm.momentum.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieConstants
import com.pooriapm.momentum.data.ExerciseLibrary
import com.pooriapm.momentum.data.LibraryExercise
import java.net.URLEncoder

/** The animations are drawn for this cream tile, so it stays the same in dark mode. */
private val Tile = Color(0xFFFBF6EE)
private val TileInk = Color(0xFFB9701C)

/** The exercise library, from the phone's copy at once and refreshed in the background. */
@Composable
fun rememberExerciseIndex(): ExerciseLibrary.Index? {
    val context = LocalContext.current
    var index by remember { mutableStateOf(ExerciseLibrary.cached) }
    LaunchedEffect(Unit) { index = ExerciseLibrary.load(context.applicationContext) ?: index }
    return index
}

/**
 * An exercise's animation on its tile, read from the phone. Lists show one still frame (many moving
 * thumbnails would redraw the screen all the time); the guide sheet plays it. Without one, a dumbbell.
 */
@Composable
fun ExerciseAnimation(anim: String?, modifier: Modifier = Modifier, playing: Boolean = false, corner: Dp = 14.dp) {
    Box(modifier.clip(RoundedCornerShape(corner)).background(Tile), contentAlignment = Alignment.Center) {
        val context = LocalContext.current
        val composition = produceState<LottieComposition?>(null, anim) {
            value = anim?.let { ExerciseLibrary.animation(context.applicationContext, it) }
        }.value
        when {
            composition == null -> Icon(
                Icons.Outlined.FitnessCenter,
                contentDescription = null,
                tint = TileInk.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxSize(0.42f),
            )
            playing -> LottieAnimation(composition, iterations = LottieConstants.IterateForever, modifier = Modifier.fillMaxSize())
            else -> LottieAnimation(composition, progress = { 0.5f }, modifier = Modifier.fillMaxSize())
        }
    }
}

/** Opens Google in the browser with "آموزش <name>", for exercises the library cannot show well. */
fun searchTutorial(context: Context, name: String) {
    val query = URLEncoder.encode("آموزش ${name.trim()}", "UTF-8")
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No browser on the phone: nothing to open.
    }
}

/** What a plan says about one exercise, shown in the guide next to the library's explanation. */
data class ExercisePlanInfo(
    val name: String,
    val exerciseKey: String?,
    val sets: Int,
    val reps: String,
    val restSeconds: Int,
    val equipment: List<String> = emptyList(),
    val note: String? = null,
    val substitution: String? = null,
)

/** One exercise explained: animation, the plan's numbers, muscles, steps, a tip and a Google search. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ExerciseSheet(info: ExercisePlanInfo, onDismiss: () -> Unit) {
    val index = rememberExerciseIndex()
    val entry: LibraryExercise? = remember(index, info) { index?.find(info.exerciseKey, info.name) }
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ExerciseAnimation(entry?.anim, Modifier.fillMaxWidth().height(230.dp), playing = true, corner = 22.dp)
            if (entry?.anim == null) {
                Text(
                    "برای این حرکت هنوز تصویر متحرک نداریم؛ با دکمه‌ی «آموزش» ویدیو و توضیحش را در گوگل ببین.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(info.name, style = MaterialTheme.typography.headlineSmall)
                entry?.let { e ->
                    val category = index?.category(e.cat)
                    Text(
                        listOfNotNull(category, e.muscles.takeIf { it.isNotBlank() }?.let { "عضلات درگیر: $it" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Fact("${info.sets.fa()} ست")
                Fact("${rangeLabel(info.reps)} تکرار")
                if (info.restSeconds > 0) Fact("${info.restSeconds.fa()} ثانیه استراحت")
                val equipment = info.equipment.map(::equipmentLabel).ifEmpty { entry?.eq.orEmpty() }
                equipment.forEach { Fact(it) }
            }
            info.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.primary) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("روش انجام", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { searchTutorial(context, info.name) }) {
                    Icon(Icons.Rounded.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("آموزش")
                }
            }
            val steps = entry?.steps.orEmpty()
            if (steps.isEmpty()) {
                Text(
                    "توضیح این حرکت در برنامه نیامده. با «آموزش» آن را در گوگل جست‌وجو کن.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            steps.forEachIndexed { i, step ->
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(colors.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text((i + 1).fa(), style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                }
            }
            entry?.tip?.let { tip ->
                Card(colors = CardDefaults.cardColors(containerColor = colors.tertiaryContainer)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(tip, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            info.substitution?.let {
                Text("اگر این حرکت برایت سخت یا ممکن نیست: $it", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Fact(text: String) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
