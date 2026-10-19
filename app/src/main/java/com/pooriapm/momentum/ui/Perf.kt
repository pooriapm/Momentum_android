package com.pooriapm.momentum.ui

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop

/** The user's choice for light mode: decided by the phone, always on, or always off. */
enum class LiteSetting { Auto, On, Off }

/** How much visual work the phone should do. */
@Immutable
data class Perf(
    /** Weak phone, battery saver or the user's choice: no glass, a flat background, few animations. */
    val lite: Boolean = false,
    /** Lite, or the system's "remove animations": decorative motion is skipped. */
    val reduceMotion: Boolean = false,
)

val LocalPerf = staticCompositionLocalOf { Perf() }

object DevicePerf {
    private const val KEY = "lite_mode"

    /**
     * A phone that struggles with blur and constant redraws: 2 GB of memory or less (or the system's
     * own low-RAM flag). Phones sold as 2 GB report about 1.8–2 GB and 3 GB phones about 2.7–2.9 GB,
     * so the line is at 2.5 GB; 3 GB and up run the full app. RAM never changes, so this is measured once.
     */
    @Volatile private var weak: Boolean? = null

    fun isWeak(context: Context): Boolean = weak ?: run {
        val am = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }.totalMem
        val result = am == null || am.isLowRamDevice || memory in 1..(2_560L shl 20)
        weak = result
        result
    }

    fun setting(prefs: SharedPreferences): LiteSetting =
        runCatching { LiteSetting.valueOf(prefs.getString(KEY, null) ?: "Auto") }.getOrDefault(LiteSetting.Auto)

    fun save(prefs: SharedPreferences, value: LiteSetting) {
        // Written at once (not in the background), so it survives the app being closed right away.
        prefs.edit().putString(KEY, value.name).commit()
    }

    /** Battery saver and "remove animations" can change at any time, so this is read again on each return to the app. */
    fun current(context: Context, setting: LiteSetting): Perf {
        val saver = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
        val noAnimations = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        val lite = when (setting) {
            LiteSetting.On -> true
            LiteSetting.Off -> false
            LiteSetting.Auto -> saver || isWeak(context)
        }
        return Perf(lite = lite, reduceMotion = lite || noAnimations)
    }
}

/** Records [backdrop] for the glass above it; in lite mode there is no glass, so nothing is recorded. */
@Composable
fun Modifier.glassSource(backdrop: LayerBackdrop): Modifier =
    if (LocalPerf.current.lite) this else layerBackdrop(backdrop)
