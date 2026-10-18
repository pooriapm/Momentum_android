package com.pooriapm.momentum.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import com.pooriapm.momentum.ui.components.GlassTab
import com.pooriapm.momentum.ui.components.GlassTabBar
import com.pooriapm.momentum.ui.components.GlassTabBarClearance
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Person
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.pooriapm.momentum.ui.theme.LocalGlow
import com.pooriapm.momentum.ui.theme.hueBackground
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pooriapm.momentum.ui.components.ErrorState
import com.pooriapm.momentum.ui.components.OfflineBanner
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.billing.BazaarBilling

private val tabs = listOf(
    GlassTab("امروز", Icons.Rounded.Home),
    GlassTab("برنامه", Icons.Rounded.CalendarMonth),
    GlassTab("پیشرفت", Icons.Rounded.Insights),
    GlassTab("حساب من", Icons.Rounded.Person),
)

/** Lists end above the floating tab bar (and the gesture bar under it), so the last item scrolls clear of the glass. */
val screenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = GlassTabBarClearance + 64.dp)

/**
 * Four destinations (Today, Plan, Progress, Me, as on the website). Content scrolls behind a
 * floating glass tab bar; switching tabs cross-fades with a slight zoom, like iOS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: AppState, billing: BazaarBilling) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
    // The tab bar's glass refracts this layer: the hue background plus everything on screen.
    val backdrop = rememberLayerBackdrop()
    // One message at a time; errors that can be retried carry a retry action.
    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = msg.text,
            actionLabel = msg.retry?.let { "تلاش دوباره" },
            withDismissAction = msg.retry == null && msg.isError,
            duration = if (msg.retry != null) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) msg.retry?.invoke()
        if (state.message == msg) state.message = null
    }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop).hueBackground(LocalGlow.current)) {
        Scaffold(
            // Edge to edge: content scrolls under the gesture bar; only the top inset is padded.
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
            containerColor = Color.Transparent,
            // Insets are handled outside (top) and by the floating bar (bottom).
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar, Modifier.navigationBarsPadding().padding(bottom = GlassTabBarClearance)) },
            topBar = { OfflineBanner(state.online) },
        ) { padding ->
            val dashboard = state.dashboard
            val loadError = state.loadError
            PullToRefreshBox(
                isRefreshing = state.loading && dashboard != null,
                onRefresh = state::refresh,
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (fadeIn(tween(240, delayMillis = 40)) + scaleIn(tween(240, delayMillis = 40), initialScale = 0.985f))
                            .togetherWith(fadeOut(tween(120)))
                    },
                    label = "tab",
                ) { current ->
                    when {
                        dashboard == null && loadError != null && !state.loading -> ErrorState(
                            loadError, state::refresh,
                            // Without a dashboard the Me tab is unreachable, so offer sign-out here.
                            secondaryLabel = "خروج از حساب", onSecondary = state::signOut,
                        )
                        dashboard == null -> Centered { CircularProgressIndicator() }
                        current == 3 -> MeTab(state, dashboard, billing)
                        dashboard.plan == null -> NoPlanTab(state, dashboard)
                        current == 0 -> TodayTab(state, dashboard) { tab = 3 }
                        current == 1 -> PlanTab(state, dashboard)
                        else -> ProgressTab(dashboard)
                    }
                }
            }
        }
        }
        GlassTabBar(tabs, tab, { tab = it }, backdrop, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}
