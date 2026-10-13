package com.pooriapm.momentum.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pooriapm.momentum.data.Api
import com.pooriapm.momentum.data.ApiException
import com.pooriapm.momentum.data.DailyCheckin
import com.pooriapm.momentum.data.Dashboard
import com.pooriapm.momentum.data.Meal
import com.pooriapm.momentum.onboarding.OnboardingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface Screen {
    data object SignIn : Screen
    data object Loading : Screen
    data object Onboarding : Screen
    data object Home : Screen
}

class AppState(val api: Api) : ViewModel() {
    var screen by mutableStateOf<Screen>(if (api.session != null) Screen.Loading else Screen.SignIn)
        private set
    var dashboard by mutableStateOf<Dashboard?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    var busyMeal by mutableStateOf<String?>(null)
        private set
    var generating by mutableStateOf(false)
        private set
    var savingCheckin by mutableStateOf(false)
        private set

    var onboarding: OnboardingState? = null
        private set

    init {
        if (api.session != null) refresh()
    }

    private fun route(data: Dashboard) {
        val needsOnboarding = data.profile.onboardingStatus !in setOf("complete", "automation_blocked")
        screen = if (needsOnboarding) {
            if (onboarding == null) onboarding = OnboardingState(api, viewModelScope) { refresh() }
            Screen.Onboarding
        } else {
            onboarding = null
            Screen.Home
        }
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                dashboard = api.dashboard().also(::route)
            } catch (e: ApiException) {
                if (api.session == null) screen = Screen.SignIn
                else if (screen == Screen.Loading) screen = Screen.Home
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    fun signIn(email: String, password: String, create: Boolean) {
        viewModelScope.launch {
            loading = true
            error = null
            notice = null
            try {
                if (create && api.signUp(email.trim(), password)) {
                    notice = "لینک تأیید به ایمیلت فرستاده شد. بعد از تأیید، همین‌جا وارد شو."
                } else {
                    if (!create) api.signIn(email.trim(), password)
                    screen = Screen.Loading
                    dashboard = api.dashboard().also(::route)
                }
            } catch (e: ApiException) {
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    fun signOut() {
        api.signOut()
        dashboard = null
        onboarding = null
        screen = Screen.SignIn
    }

    private fun mutate(slotKey: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            busyMeal = slotKey
            try {
                block()
                dashboard = api.dashboard()
            } catch (e: ApiException) {
                error = e.message
            } finally {
                busyMeal = null
            }
        }
    }

    fun toggleMeal(meal: Meal, localDate: String) {
        val option = meal.chosenOption ?: return
        mutate(meal.slotKey) {
            if (meal.completionStatus == "completed") api.undoMeal(localDate, meal.slotKey, option.optionKey)
            else api.completeMeal(localDate, meal.slotKey, option.optionKey)
        }
    }

    fun chooseOption(meal: Meal, localDate: String, optionKey: String) {
        if (optionKey == meal.chosenOption?.optionKey) return
        mutate(meal.slotKey) { api.selectMeal(localDate, meal.slotKey, optionKey) }
    }

    fun saveCheckin(checkin: DailyCheckin, onDone: () -> Unit) {
        val date = dashboard?.localDate ?: return
        viewModelScope.launch {
            savingCheckin = true
            try {
                api.saveDailyCheckin(date, checkin)
                dashboard = api.dashboard()
                notice = "چک‌این امروز ثبت شد. برنامه این ماه تغییری نکرد."
                onDone()
            } catch (e: ApiException) {
                error = e.message
            } finally {
                savingCheckin = false
            }
        }
    }

    /** For accounts that finished setup (maybe on the website) but have no plan yet. */
    fun generatePlan() {
        if (generating) return
        viewModelScope.launch {
            generating = true
            error = null
            try {
                // One key per account and day: retries resume the same job instead of starting another.
                val key = "android:${api.session?.userId}:${dashboard?.localDate}:generate"
                var status = api.requestPlanGeneration(key)
                repeat(20) { if (status == "in_progress") { delay(1500); status = api.requestPlanGeneration(key) } }
                repeat(36) {
                    val next = api.dashboard()
                    dashboard = next
                    if (next.plan != null) return@launch
                    delay(5000)
                }
                notice = "ساخت برنامه ادامه دارد؛ کمی بعد دوباره سر بزن."
            } catch (e: ApiException) {
                error = when (e.code) {
                    "entitlement_required", "PAYMENT_METHOD_REQUIRED", "quota_exceeded" -> "برای ساخت برنامه این ماه، عضویت را از «حساب من» فعال کن."
                    else -> e.message
                }
            } finally {
                generating = false
            }
        }
    }
}
