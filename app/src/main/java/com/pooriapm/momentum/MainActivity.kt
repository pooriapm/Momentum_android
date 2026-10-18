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
import androidx.compose.material3.CircularProgressIndicator
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
import com.pooriapm.momentum.ui.AppState
import com.pooriapm.momentum.ui.Centered
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
                AppState(Api(applicationContext), Connectivity(applicationContext)) as T
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
                    // Home handles its own insets: it scrolls under the gesture bar, as on iOS.
                    val insets = if (state.screen == Screen.Home) Modifier else Modifier.safeDrawingPadding()
                    Box(insets) {
                        when (state.screen) {
                            Screen.SignIn -> SignInScreen(state)
                            Screen.Loading -> Centered { CircularProgressIndicator() }
                            Screen.Onboarding -> state.onboarding?.let { OnboardingScreen(it) }
                            Screen.Home -> HomeScreen(state, billing)
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
