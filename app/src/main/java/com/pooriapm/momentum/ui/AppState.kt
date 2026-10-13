package com.pooriapm.momentum.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pooriapm.momentum.data.Api
import com.pooriapm.momentum.data.ApiException
import com.pooriapm.momentum.data.Dashboard
import com.pooriapm.momentum.data.Meal
import kotlinx.coroutines.launch

sealed interface Screen {
    data object SignIn : Screen
    data object Home : Screen
}

class AppState(val api: Api) : ViewModel() {
    var screen by mutableStateOf<Screen>(if (api.session != null) Screen.Home else Screen.SignIn)
        private set
    var dashboard by mutableStateOf<Dashboard?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)
    var busyMeal by mutableStateOf<String?>(null)
        private set

    init {
        if (api.session != null) refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            loading = true
            error = null
            try {
                dashboard = api.dashboard()
            } catch (e: ApiException) {
                if (api.session == null) screen = Screen.SignIn
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
                    screen = Screen.Home
                    dashboard = api.dashboard()
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
        screen = Screen.SignIn
    }

    fun toggleMeal(meal: Meal, localDate: String) {
        val option = meal.chosenOption ?: return
        viewModelScope.launch {
            busyMeal = meal.slotKey
            try {
                if (meal.completionStatus == "completed") api.undoMeal(localDate, meal.slotKey, option.optionKey)
                else api.completeMeal(localDate, meal.slotKey, option.optionKey)
                dashboard = api.dashboard()
            } catch (e: ApiException) {
                error = e.message
            } finally {
                busyMeal = null
            }
        }
    }
}
