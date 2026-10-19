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
import com.pooriapm.momentum.data.WorkoutSession
import kotlinx.serialization.json.JsonObject
import com.pooriapm.momentum.data.asApi
import com.pooriapm.momentum.data.findMeal
import com.pooriapm.momentum.data.withMeal
import com.pooriapm.momentum.data.persianError
import com.pooriapm.momentum.onboarding.OnboardingState
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

sealed interface Screen {
    data object SignIn : Screen
    data object Loading : Screen
    data object Onboarding : Screen
    data object Home : Screen
}

/** A message for the user; [retry] is offered as an action when trying again can help. */
data class UiMessage(val text: String, val retry: (() -> Unit)? = null, val isError: Boolean = true)

class AppState(
    val api: Api,
    private val connectivity: Connectivity,
    /** Rebuilds local reminders after data changes (null in previews). */
    private val onPlanChanged: ((Dashboard?, Boolean) -> Unit)? = null,
    /** Small on-device store (water until the server has it, widget snapshot). */
    private val local: android.content.SharedPreferences? = null,
    /** Today's water changed in the app (the widget shows it too). */
    private val onWaterChanged: ((Int) -> Unit)? = null,
) : ViewModel() {
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
    /** Meals (date|slot) whose change is still being saved; their controls ignore taps meanwhile. */
    var pendingMeals by mutableStateOf<Set<String>>(emptySet())
        private set
    private var syncJob: Job? = null
    var generating by mutableStateOf(false)
        private set
    var savingCheckin by mutableStateOf(false)
        private set

    var onboarding: OnboardingState? = null
        private set

    /** Today's water in ml: the larger of the phone's and the server's count (never goes back). */
    var waterMl by mutableStateOf(0)
        private set
    private var waterSync: Job? = null

    private fun loadWater() {
        val date = dashboard?.localDate ?: return
        val saved = local?.getInt("water:$date", 0) ?: 0
        val server = dashboard?.waterToday?.takeIf { it.localDate == date }?.waterMl ?: 0
        waterMl = maxOf(saved, server)
    }

    /** One glass (250 ml) more or less. Saved on the phone at once, synced quietly after a pause. */
    fun changeWater(glasses: Int) {
        val date = dashboard?.localDate ?: return
        waterMl = (waterMl + glasses * 250).coerceIn(0, 6000)
        local?.edit()?.putInt("water:$date", waterMl)?.apply()
        onWaterChanged?.invoke(waterMl)
        waterSync?.cancel()
        waterSync = scope.launch {
            delay(1500)
            // Until the server has save-water this fails quietly; the phone keeps the count.
            runCatching { api.saveWater(date, waterMl, dashboard?.profile?.timezone) }
        }
    }

    /** Today's workout session (null = not started); [workoutLoaded] tells "none" from "unknown". */
    var workout by mutableStateOf<WorkoutSession?>(null)
        private set
    var workoutLoaded by mutableStateOf(false)
        private set
    var workoutBusy by mutableStateOf(false)
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
        pendingMeals = emptySet()
        message = UiMessage(error.message.orEmpty())
    }
    private val scope: CoroutineScope get() = CoroutineScope(viewModelScope.coroutineContext + guard)

    init {
        scope.launch {
            snapshotFlow { dashboard to (workout?.status == "completed") }
                .distinctUntilChanged()
                .collect { (data, workoutDone) -> onPlanChanged?.invoke(data, workoutDone) }
        }
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
                val previousDate = dashboard?.localDate
                dashboard = api.dashboard().also(::route)
                loadWater()
                if (previousDate != dashboard?.localDate) {
                    workout = null
                    workoutLoaded = false
                }
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
            val widgetChanged = (local?.getLong("widget_changed_at", 0L) ?: 0L) > lastLoadedAt
            if (widgetChanged || System.currentTimeMillis() - lastLoadedAt > 5 * 60_000) refresh()
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

    fun mealPending(localDate: String, slotKey: String) = "$localDate|$slotKey" in pendingMeals

    /**
     * Optimistic meal change: the card shows the new state at once ([optimistic] edits the meal in
     * the cached dashboard), the request runs in the background, and on failure that meal alone is
     * put back as it was and the error shown. The day's totals are refreshed after the last change.
     */
    private fun mutate(localDate: String, slotKey: String, retry: () -> Unit, optimistic: (Meal) -> Meal, block: suspend () -> Unit) {
        val key = "$localDate|$slotKey"
        if (!online) return offlineMessage(retry)
        if (key in pendingMeals) return
        val before = dashboard?.findMeal(localDate, slotKey) ?: return
        dashboard = dashboard?.withMeal(localDate, slotKey, optimistic)
        pendingMeals = pendingMeals + key
        scope.launch {
            try {
                block()
                syncDashboardSoon()
            } catch (raw: Exception) {
                // Roll back only this meal; other changes made meanwhile stay.
                dashboard = dashboard?.withMeal(localDate, slotKey) { before }
                val error = raw.asApi()
                report(error, retry.takeUnless { error.code == "local_date_mismatch" })
                // The card was stale (another device, or the day rolled over): show the truth.
                if (error.kind == ErrorKind.Conflict || error.kind == ErrorKind.NotFound) refresh()
            } finally {
                pendingMeals = pendingMeals - key
            }
        }
    }

    /** One quiet reload once changes settle, so several quick ticks cost one dashboard call. */
    private fun syncDashboardSoon() {
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(1200)
            if (pendingMeals.isNotEmpty()) return@launch // the last change will schedule another
            runCatching { api.dashboard() }.onSuccess {
                if (pendingMeals.isEmpty()) {
                    dashboard = it
                    lastLoadedAt = System.currentTimeMillis()
                }
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
        mutate(localDate, meal.slotKey, { completeMeal(meal, localDate) }, { it.copy(completionStatus = "completed") }) {
            api.completeMeal(localDate, meal.slotKey, option.optionKey)
        }
    }

    fun skipMeal(meal: Meal, localDate: String) {
        if (meal.completionStatus != "planned") return
        val option = meal.chosenOption ?: return
        mutate(localDate, meal.slotKey, { skipMeal(meal, localDate) }, { it.copy(completionStatus = "skipped") }) {
            api.skipMeal(localDate, meal.slotKey, option.optionKey)
        }
    }

    /** Back to planned, from either eaten or skipped. */
    fun undoMeal(meal: Meal, localDate: String) {
        if (meal.completionStatus == "planned") return
        val option = meal.chosenOption ?: return
        mutate(localDate, meal.slotKey, { undoMeal(meal, localDate) }, { it.copy(completionStatus = "planned") }) {
            api.undoMeal(localDate, meal.slotKey, option.optionKey)
        }
    }

    fun chooseOption(meal: Meal, localDate: String, optionKey: String) {
        // Only a planned meal can switch; the server locks eaten and skipped ones.
        if (optionKey == meal.chosenOption?.optionKey || meal.completionStatus != "planned") return
        mutate(localDate, meal.slotKey, { chooseOption(meal, localDate, optionKey) }, { it.copy(selectedOptionKey = optionKey) }) {
            api.selectMeal(localDate, meal.slotKey, optionKey)
        }
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

    /** The server's key for today's workout: "<plan id>-<day index>". */
    fun todayWorkoutKey(): String? {
        val plan = dashboard?.plan ?: return null
        if (plan.day.workout == null) return null
        return plan.id?.let { "$it-${plan.day.dayIndex}" }
    }

    fun loadWorkout() {
        val date = dashboard?.localDate ?: return
        val key = todayWorkoutKey() ?: return
        if (!online) return
        scope.launch {
            try {
                workout = api.workoutSession(date, key)
                workoutLoaded = true
            } catch (raw: Exception) {
                raw.asApi() // logged; the card keeps its "start" state
            }
        }
    }

    /** Starts (or reopens) today's workout. Safe to repeat: one session per day and workout. */
    fun startWorkout(onReady: () -> Unit) {
        val date = dashboard?.localDate ?: return
        val key = todayWorkoutKey() ?: return
        if (workout != null) return onReady()
        workoutAction({ startWorkout(onReady) }) { api.startWorkout(date, key).also { onReady() } }
    }

    fun workoutStep(action: String, exerciseKey: String? = null, setNumber: Int? = null, values: JsonObject = JsonObject(emptyMap())) {
        val session = workout ?: return
        workoutAction({ workoutStep(action, exerciseKey, setNumber, values) }) {
            api.mutateWorkout(session.id, action, exerciseKey, setNumber, values)
        }
    }

    private fun workoutAction(retry: () -> Unit, block: suspend () -> WorkoutSession) {
        if (workoutBusy) return
        if (!online) return offlineMessage(retry)
        scope.launch {
            workoutBusy = true
            try {
                workout = block()
                workoutLoaded = true
            } catch (raw: Exception) {
                val error = raw.asApi()
                report(error, retry)
                // Stale view (closed elsewhere, day rolled over): show what the server has.
                if (error.kind == ErrorKind.Conflict || error.kind == ErrorKind.NotFound || error.kind == ErrorKind.Invalid) loadWorkout()
            } finally {
                workoutBusy = false
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
