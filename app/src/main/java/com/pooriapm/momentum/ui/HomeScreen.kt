package com.pooriapm.momentum.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.pooriapm.momentum.billing.BazaarBilling

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("امروز", Icons.Outlined.Home),
    Tab("برنامه", Icons.Outlined.CalendarMonth),
    Tab("پیشرفت", Icons.Outlined.Insights),
    Tab("حساب من", Icons.Outlined.Person),
)

val screenPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 24.dp)

/** Four destinations in an M3 navigation bar, the same IA as the website (Today, Plan, Progress, Me). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(state: AppState, billing: BazaarBilling) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val snackbar = remember { SnackbarHostState() }
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

    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { OfflineBanner(state.online) },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
    ) { padding ->
        val dashboard = state.dashboard
        val loadError = state.loadError
        PullToRefreshBox(
            isRefreshing = state.loading && dashboard != null,
            onRefresh = state::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                dashboard == null && loadError != null && !state.loading -> ErrorState(
                    loadError, state::refresh,
                    // Without a dashboard the Me tab is unreachable, so offer sign-out here.
                    secondaryLabel = "خروج از حساب", onSecondary = state::signOut,
                )
                dashboard == null -> Centered { CircularProgressIndicator() }
                tab == 3 -> MeTab(state, dashboard, billing)
                dashboard.plan == null -> NoPlanTab(state, dashboard)
                tab == 0 -> TodayTab(state, dashboard) { tab = 3 }
                tab == 1 -> PlanTab(state, dashboard)
                else -> ProgressTab(dashboard)
            }
        }
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}
