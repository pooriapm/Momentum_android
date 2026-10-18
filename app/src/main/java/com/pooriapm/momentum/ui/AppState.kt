package com.pooriapm.momentum.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pooriapm.momentum.data.Api
import com.pooriapm.momentum.data.ApiException
import com.pooriapm.momentum.data.Connectivity
import com.pooriapm.momentum.data.DailyCheckin
import com.pooriapm.momentum.data.Dashboard
import com.pooriapm.momentum.data.ErrorKind
import com.pooriapm.momentum.data.Meal
import com.pooriapm.momentum.data.ShoppingPurchase
import com.pooriapm.momentum.data.asApi
import com.pooriapm.momentum.data.persianError
import com.pooriapm.momentum.onboarding.OnboardingState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface Screen {
    data object SignIn : Screen
    data object Loading : Screen
    data object Onboarding : Screen
    data object Home : Screen
}

/** A message for the user; [retry] is offered as an action when trying again can help. */
data class UiMessage(val text: String, val retry: (() -> Unit)? = null, val isError: Boolean = true)

class AppState(val api: Api, private val connectivity: Connectivity) : ViewModel() {
    var screen by mutableStateOf<Screen>(if (api.session != null) Screen.Loading else Screen.SignIn)
        private set
    var dashboard by mutableStateOf<Dashboard?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var online by mutableStateOf(true)
        private set

    /** Inline messages on the sign-in screen. */
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)

    /** Transient messages on the signed-in screens (snackbar, with an optional retry action). */
    var message by mutableStateOf<UiMessage?>(null)

    /** Why the dashboard could not load; shown full-screen with a retry, never an endless spinner. */
    var loadError by mutableStateOf<ApiException?>(null)
        private set
    var busyMeal by mutableStateOf<String?>(null)
        private set
    var generating by mutableStateOf(false)
        private set
    var savingCheckin by mutableStateOf(false)
        private set

    var onboarding: OnboardingState? = null
        private set

    /** Shopping purchases of the active plan (server-synced). null until first loaded. */
    var shopping by mutableStateOf<List<ShoppingPurchase>?>(null)
        private set
    var shoppingError by mutableStateOf<ApiException?>(null)
        private set
    /** Line keys with a request in flight, so double taps cannot buy twice. */
    var shoppingBusy by mutableStateOf<Set<String>>(emptySet())
        private set
    private var shoppingPlan: String? = null

    private var lastLoadedAt = 0L
    private var refreshJob: Job? = null
    private var autoRetries = 0

    /** Last line of defence: an unexpected error in any coroutine becomes a message, not a crash. */
    private val guard = CoroutineExceptionHandler { _, throwable ->
        val error = runCatching { throwable.asApi() }.getOrNull() ?: return@CoroutineExceptionHandler
        loading = false
        busyMeal = null
        message = UiMessage(error.message.orEmpty())
    }
    private val scope: CoroutineScope get() = CoroutineScope(viewModelScope.coroutineContext + guard)

    init {
        api.onSessionExpired = {
            viewModelScope.launch(Dispatchers.Main) { toSignIn(persianError("session_expired")) }
        }
        scope.launch {
            connectivity.online.collect { now ->
                val cameBack = now && !online
                online = now
                // Reconnected after a failed load: retry on our own instead of waiting for a tap.
                if (cameBack && (loadError != null || dashboard == null) && api.session != null) refresh()
            }
        }
        if (api.session != null) refresh()
    }

    override fun onCleared() {
        connectivity.close()
    }

    private fun route(data: Dashboard) {
        val needsOnboarding = data.profile.onboardingStatus !in setOf("complete", "automation_blocked")
        screen = if (needsOnboarding) {
            if (onboarding == null) onboarding = OnboardingState(api, scope, ::onlineNow) { refresh() }
            Screen.Onboarding
        } else {
            onboarding = null
            Screen.Home
        }
    }

    private fun onlineNow() = online

    private fun toSignIn(reason: String?) {
        dashboard = null
        onboarding = null
        loadError = null
        message = null
        notice = null
        error = reason
        screen = Screen.SignIn
    }

    /** Shows [error] the standard way: back to sign-in when the session ended, else a message. */
    private fun report(error: ApiException, retry: (() -> Unit)? = null) {
        if (error.kind == ErrorKind.SessionExpired && api.session == null) {
            toSignIn(error.message)
            return
        }
        message = UiMessage(error.message.orEmpty(), retry?.takeIf { error.retryable })
    }

    private fun offlineMessage(retry: () -> Unit) {
        message = UiMessage(persianError("offline", ErrorKind.Offline), retry)
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            loading = true
            try {
                dashboard = api.dashboard().also(::route)
                loadError = null
                autoRetries = 0
                lastLoadedAt = System.currentTimeMillis()
            } catch (raw: Exception) {
                val e = raw.asApi()
                if (api.session == null) {
                    toSignIn(if (e.kind == ErrorKind.SessionExpired) e.message else null)
                    return@launch
                }
                if (screen == Screen.Loading) screen = Screen.Home
                if (dashboard == null) loadError = e else report(e, ::refresh)
                // A blip right at launch (radio waking up, captive network) should not need a tap.
                if (dashboard == null && e.retryable && autoRetries < 3) {
                    autoRetries++
                    scope.launch {
                        delay(3000L * autoRetries)
                        if (dashboard == null) refresh()
                    }
                }
            } finally {
                loading = false
            }
        }
    }

    /** App back in the foreground: keep the session warm and reload data older than 5 minutes. */
    fun onForeground() {
        if (api.session == null) return
        scope.launch {
            api.keepSessionAlive()
            if (System.currentTimeMillis() - lastLoadedAt > 5 * 60_000) refresh()
        }
    }

    fun signIn(email: String, password: String, create: Boolean) {
        if (!online) {
            error = persianError("offline", ErrorKind.Offline)
            return
        }
        scope.launch {
            loading = true
            error = null
            notice = null
            try {
                if (create && api.signUp(email.trim(), password)) {
                    notice = "لینک تأیید به ایمیلت فرستاده شد. بعد از تأیید، همین‌جا وارد شو."
                    return@launch
                }
                if (!create) api.signIn(email.trim(), password)
                screen = Screen.Loading
            } catch (raw: Exception) {
                error = raw.asApi().message
                return@launch
            } finally {
                loading = false
            }
            refresh()
        }
    }

    /** Password reset or confirmation resend; the answer is the same either way so emails are not enumerable. */
    fun sendAuthEmail(email: String, reset: Boolean) {
        if (!online) {
            error = persianError("offline", ErrorKind.Offline)
            return
        }
        scope.launch {
            loading = true
            error = null
            notice = null
            try {
                if (reset) api.requestPasswordReset(email.trim()) else api.resendConfirmation(email.trim())
                notice = if (reset) "اگر حسابی با این ایمیل باشد، لینک تغییر رمز فرستاده شد. پوشه اسپم را هم ببین."
                else "اگر این حساب هنوز تأیید نشده باشد، لینک تأیید دوباره فرستاده شد. پوشه اسپم را هم ببین."
            } catch (raw: Exception) {
                error = raw.asApi().message
            } finally {
                loading = false
            }
        }
    }

    fun signOut() {
        scope.launch {
            api.signOut()
            toSignIn(null)
        }
    }

    private fun mutate(slotKey: String, retry: () -> Unit, block: suspend () -> Unit) {
        if (!online) return offlineMessage(retry)
        if (busyMeal != null) return
        scope.launch {
            busyMeal = slotKey
            try {
                block()
                dashboard = api.dashboard()
                lastLoadedAt = System.currentTimeMillis()
            } catch (raw: Exception) {
                val error = raw.asApi()
                report(error, retry.takeUnless { error.code == "local_date_mismatch" })
                // The card was stale (another device, or the day rolled over): show the truth.
                if (error.kind == ErrorKind.Conflict || error.kind == ErrorKind.NotFound) refresh()
            } finally {
                busyMeal = null
            }
        }
    }

    /**
     * Meal status changes. Each one checks the meal's current server status first, so a double tap
     * or a stale card can never send "complete" for a meal that is already done (or undo nothing).
     */
    fun completeMeal(meal: Meal, localDate: String) {
        if (meal.completionStatus == "completed") return
        val option = meal.chosenOption ?: return
        mutate(meal.slotKey, { completeMeal(meal, localDate) }) { api.completeMeal(localDate, meal.slotKey, option.optionKey) }
    }

    fun skipMeal(meal: Meal, localDate: String) {
        if (meal.completionStatus != "planned") return
        val option = meal.chosenOption ?: return
        mutate(meal.slotKey, { skipMeal(meal, localDate) }) { api.skipMeal(localDate, meal.slotKey, option.optionKey) }
    }

    /** Back to planned, from either eaten or skipped. */
    fun undoMeal(meal: Meal, localDate: String) {
        if (meal.completionStatus == "planned") return
        val option = meal.chosenOption ?: return
        mutate(meal.slotKey, { undoMeal(meal, localDate) }) { api.undoMeal(localDate, meal.slotKey, option.optionKey) }
    }

    fun chooseOption(meal: Meal, localDate: String, optionKey: String) {
        // Only a planned meal can switch; the server locks eaten and skipped ones.
        if (optionKey == meal.chosenOption?.optionKey || meal.completionStatus != "planned") return
        mutate(meal.slotKey, { chooseOption(meal, localDate, optionKey) }) { api.selectMeal(localDate, meal.slotKey, optionKey) }
    }

    fun saveCheckin(checkin: DailyCheckin, onDone: () -> Unit) {
        val date = dashboard?.localDate ?: return
        if (!online) return offlineMessage { saveCheckin(checkin, onDone) }
        if (savingCheckin) return
        scope.launch {
            savingCheckin = true
            try {
                api.saveDailyCheckin(date, checkin)
                onDone()
                message = UiMessage("گزارش امروز ثبت شد. برنامه این ماه تغییری نکرد.", isError = false)
                dashboard = api.dashboard()
            } catch (raw: Exception) {
                report(raw.asApi()) { saveCheckin(checkin, onDone) }
            } finally {
                savingCheckin = false
            }
        }
    }

    /** Loads purchases once per plan version; [force] reloads (pull to refresh, retry). */
    fun loadShopping(force: Boolean = false) {
        val plan = dashboard?.plan ?: return
        val version = plan.versionId ?: "${plan.validFrom}:${plan.name}"
        if (!force && shoppingPlan == version && shopping != null) return
        if (!online) {
            shoppingError = ApiException("offline", persianError("offline", ErrorKind.Offline), ErrorKind.Offline)
            return
        }
        scope.launch {
            try {
                shopping = api.shoppingPurchases()
                shoppingPlan = version
                shoppingError = null
            } catch (raw: Exception) {
                shoppingError = raw.asApi()
            }
        }
    }

    /** Records buying [line]'s remaining amount for [scopeOf]. Shown at once, rolled back on failure. */
    fun buyShopping(line: ShoppingLine, scopeOf: ShoppingScope) {
        val date = dashboard?.localDate ?: return
        val current = shopping ?: return
        if (line.covered || line.key in shoppingBusy) return
        if (!online) return offlineMessage { buyShopping(line, scopeOf) }
        val unit = line.unit
        val temp = ShoppingPurchase("local-${System.nanoTime()}", line.itemKey, unit, line.toBuy, scopeOf.wire, date)
        shopping = current + temp
        shoppingBusy = shoppingBusy + line.key
        scope.launch {
            try {
                val saved = api.recordShoppingPurchase(line.itemKey, unit, line.toBuy, scopeOf.wire, date)
                shopping = shopping.orEmpty().map { if (it.id == temp.id) saved else it }
            } catch (raw: Exception) {
                shopping = shopping.orEmpty().filterNot { it.id == temp.id }
                val error = raw.asApi()
                report(error) { buyShopping(line, scopeOf) }
                if (error.code == "local_date_mismatch") refresh()
            } finally {
                shoppingBusy = shoppingBusy - line.key
            }
        }
    }

    /** Removes the newest purchase of [line] (a mistaken tick). */
    fun unbuyShopping(line: ShoppingLine) {
        val current = shopping ?: return
        val purchase = lastPurchaseFor(line, current) ?: return
        if (line.key in shoppingBusy || purchase.id.startsWith("local-")) return
        if (!online) return offlineMessage { unbuyShopping(line) }
        shopping = current - purchase
        shoppingBusy = shoppingBusy + line.key
        scope.launch {
            try {
                api.undoShoppingPurchase(purchase.id)
            } catch (raw: Exception) {
                val error = raw.asApi()
                if (error.code == "shopping_purchase_not_found") return@launch // already gone
                shopping = shopping.orEmpty() + purchase
                report(error) { unbuyShopping(line) }
            } finally {
                shoppingBusy = shoppingBusy - line.key
            }
        }
    }

    /** For accounts that finished setup (maybe on the website) but have no plan yet. */
    fun generatePlan() {
        if (generating) return
        if (!online) return offlineMessage(::generatePlan)
        scope.launch {
            generating = true
            try {
                // One key per account and day: retries resume the same job instead of starting another.
                val key = "android:${api.session?.userId}:${dashboard?.localDate}:generate"
                val starter = api.startMonthlyPlan(key)
                repeat(36) {
                    val next = api.dashboard()
                    dashboard = next
                    if (next.plan != null) {
                        if (starter) message = UiMessage("ساخت هوشمند فعلاً خاموش است؛ برنامهٔ شروع از کاتالوگ برایت فعال شد.", isError = false)
                        return@launch
                    }
                    delay(5000)
                }
                message = UiMessage("ساخت برنامه ادامه دارد؛ کمی بعد دوباره سر بزن.", isError = false)
            } catch (raw: Exception) {
                report(raw.asApi(), ::generatePlan)
            } finally {
                generating = false
            }
        }
    }
}
