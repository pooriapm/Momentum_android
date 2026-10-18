package com.pooriapm.momentum

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Modifier
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.pooriapm.momentum.billing.BazaarBilling
import com.pooriapm.momentum.data.Api
import com.pooriapm.momentum.data.Connectivity
import com.pooriapm.momentum.onboarding.OnboardingScreen
import com.pooriapm.momentum.reminders.Reminders
import com.pooriapm.momentum.ui.AppState
import com.pooriapm.momentum.ui.BrandSplash
import com.pooriapm.momentum.ui.HomeScreen
import com.pooriapm.momentum.ui.Screen
import com.pooriapm.momentum.ui.SignInScreen
import com.pooriapm.momentum.ui.theme.GlowBackground
import com.pooriapm.momentum.ui.theme.MomentumTheme

class MainActivity : ComponentActivity() {
    private val state: AppState by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppState(Api(applicationContext), Connectivity(applicationContext)) { dashboard, workoutDone ->
                    // Signed out: no reminders; otherwise follow the plan.
                    if (dashboard == null) Reminders.clear(applicationContext)
                    else Reminders.sync(applicationContext, dashboard, workoutDone)
                } as T
        }
    }
    private lateinit var billing: BazaarBilling

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent bars; their icons follow the light/dark theme so they stay readable.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        Reminders.ensureChannel(this)
        billing = BazaarBilling(this, state.api, lifecycleScope)
        billing.connect {
            if (state.api.session != null) billing.recoverPendingPurchases { state.refresh() }
        }
        // Coming back to the app keeps the session warm and reloads stale data.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) = state.onForeground()
        })
        setContent {
            MomentumTheme {
                // The glow runs edge to edge (behind the system bars); content stays inside them.
                GlowBackground {
                    // The intro plays once per launch (not on rotation) and also covers any loading.
                    var introDone by rememberSaveable { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(2800)
                        introDone = true
                    }
                    val splash = !introDone || state.screen == Screen.Loading
                    AnimatedContent(
                        targetState = splash,
                        transitionSpec = {
                            (fadeIn(tween(450, delayMillis = 120)) + scaleIn(tween(450, delayMillis = 120), initialScale = 0.97f))
                                .togetherWith(fadeOut(tween(380)) + scaleOut(tween(380), targetScale = 1.08f))
                        },
                        label = "splash",
                    ) { showSplash ->
                        if (showSplash) {
                            BrandSplash()
                        } else {
                            // Home handles its own insets: it scrolls under the gesture bar, as on iOS.
                            val insets = if (state.screen == Screen.Home) Modifier else Modifier.safeDrawingPadding()
                            Box(insets) {
                                when (state.screen) {
                                    Screen.SignIn -> SignInScreen(state)
                                    Screen.Loading -> Unit
                                    Screen.Onboarding -> state.onboarding?.let { OnboardingScreen(it) }
                                    Screen.Home -> HomeScreen(state, billing)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        billing.disconnect()
        super.onDestroy()
    }
}
