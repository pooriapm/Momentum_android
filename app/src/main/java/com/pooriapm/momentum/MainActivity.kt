package com.pooriapm.momentum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.pooriapm.momentum.billing.BazaarBilling
import com.pooriapm.momentum.data.Api
import com.pooriapm.momentum.onboarding.OnboardingScreen
import com.pooriapm.momentum.ui.AppState
import com.pooriapm.momentum.ui.Centered
import com.pooriapm.momentum.ui.HomeScreen
import com.pooriapm.momentum.ui.Screen
import com.pooriapm.momentum.ui.SignInScreen
import com.pooriapm.momentum.ui.theme.MomentumTheme

class MainActivity : ComponentActivity() {
    private val state: AppState by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppState(Api(applicationContext)) as T
        }
    }
    private lateinit var billing: BazaarBilling

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        billing = BazaarBilling(this, state.api, lifecycleScope)
        billing.connect {
            if (state.api.session != null) billing.recoverPendingPurchases { state.refresh() }
        }
        setContent {
            MomentumTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
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

    override fun onDestroy() {
        billing.disconnect()
        super.onDestroy()
    }
}
