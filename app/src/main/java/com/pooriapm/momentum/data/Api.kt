package com.pooriapm.momentum.data

import android.content.Context
import com.pooriapm.momentum.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.util.UUID
import java.util.concurrent.TimeUnit

val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

data class OnboardingDraft(val step: String, val values: Map<String, String>)

/**
 * Talks to the same Supabase project as the website: GoTrue for sign-in, PostgREST for the
 * onboarding draft (row-level security keeps it per user) and the Edge Functions for the rest.
 *
 * Reliability rules:
 * - Reads, and writes that carry an Idempotency-Key, are retried on network errors, timeouts,
 *   429 and 5xx with exponential backoff (Retry-After is honoured). Other writes never retry.
 * - The access token is refreshed shortly before it expires; a 401 triggers one refresh and one
 *   retry. Refreshes are serialized because Supabase refresh tokens are single-use.
 * - Only a refresh token the server rejects ends the session; being offline never does.
 */
class Api(context: Context) {
    private val store = SessionStore(context)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false) // retries are decided below, per request kind
        .build()
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val refreshLock = Mutex()

    var session: Session? = store.load()
        private set

    /** Called when the session ends without the user asking (revoked or expired by inactivity). */
    var onSessionExpired: (() -> Unit)? = null

    private fun saveSession(value: Session?) {
        session = value
        if (value == null) store.clear() else store.save(value)
    }

    private fun endSession() {
        if (session == null) return
        saveSession(null)
        onSessionExpired?.invoke()
    }

    // ---- HTTP core ----

    private suspend fun execute(
        method: String,
        url: String,
        body: JsonElement?,
        token: String?,
        headers: Map<String, String>,
    ): JsonElement = withContext(Dispatchers.IO) {
        if (!base.startsWith("https://") || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
            throw ApiException("not_configured", persianError("not_configured"), ErrorKind.NotConfigured, retryable = false)
        }
        val request = Request.Builder()
            .url(url)
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            // Only a signed-in user's JWT goes in Authorization. New-style publishable keys
            // (sb_publishable_…) are not JWTs and belong in apikey alone.
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .header("X-Momentum-Client", "android/${BuildConfig.VERSION_NAME}")
            .header("Accept", "application/json")
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .method(method, body?.toString()?.toRequestBody(jsonType))
            .build()
        val response = try {
            http.newCall(request).execute()
        } catch (error: SocketTimeoutException) {
            throw ApiException("timeout", persianError("timeout", ErrorKind.Timeout), ErrorKind.Timeout)
        } catch (error: InterruptedIOException) {
            throw ApiException("timeout", persianError("timeout", ErrorKind.Timeout), ErrorKind.Timeout)
        } catch (error: IOException) {
            throw ApiException("offline", persianError("offline", ErrorKind.Offline), ErrorKind.Offline)
        }
        response.use {
            val text = try {
                it.body?.string().orEmpty()
            } catch (error: IOException) {
                throw ApiException("offline", persianError("offline", ErrorKind.Offline), ErrorKind.Offline)
            }
            val parsed = runCatching { json.parseToJsonElement(text) }.getOrNull()
            if (!it.isSuccessful) {
                val obj = parsed as? JsonObject ?: JsonObject(emptyMap())
                val error = obj["error"] as? JsonObject
                val code = error?.get("code")?.jsonPrimitive?.contentOrNull
                    ?: obj["error_code"]?.jsonPrimitive?.contentOrNull
                    ?: (obj["code"] as? JsonPrimitive)?.takeIf { p -> p.isString }?.contentOrNull
                    ?: (obj["error"] as? JsonPrimitive)?.contentOrNull
                    ?: "http_${it.code}"
                val kind = kindForStatus(it.code, code)
                val retryAfter = it.header("Retry-After")?.toLongOrNull()
                val detail = (error?.get("message") as? JsonPrimitive)?.contentOrNull
                    ?: (obj["msg"] as? JsonPrimitive)?.contentOrNull
                throw ApiException(code, persianError(code, kind), kind, retryAfterSeconds = retryAfter, detail = detail)
            }
            parsed ?: if (text.isBlank()) JsonObject(emptyMap())
            else throw ApiException("invalid_response", persianError("invalid_response", ErrorKind.Server), ErrorKind.Server)
        }
    }

    /** Runs [attempt] with exponential backoff when the request is safe to repeat. */
    private suspend fun <T> withRetry(safeToRetry: Boolean, attempt: suspend () -> T): T {
        var delayMs = 600L
        repeat(MAX_ATTEMPTS - 1) {
            try {
                return attempt()
            } catch (error: ApiException) {
                if (!safeToRetry || !error.retryable) throw error
                val wait = error.retryAfterSeconds?.let { (it * 1000).coerceAtMost(10_000) } ?: delayMs
                delay(wait)
                delayMs *= 2
            }
        }
        return attempt()
    }

    /** Anonymous call (sign-in, sign-up, password reset). */
    private suspend fun public(method: String, url: String, body: JsonElement?, safeToRetry: Boolean = false) =
        withRetry(safeToRetry) { execute(method, url, body, null, emptyMap()) }

    /** Signed-in call: fresh token first, one refresh-and-retry on 401. */
    private suspend fun authed(
        method: String,
        url: String,
        body: JsonElement?,
        headers: Map<String, String> = emptyMap(),
        safeToRetry: Boolean = method == "GET" || headers.containsKey("Idempotency-Key"),
    ): JsonElement = withRetry(safeToRetry) {
        try {
            execute(method, url, body, accessToken(), headers)
        } catch (error: ApiException) {
            if (error.kind != ErrorKind.SessionExpired) throw error
            execute(method, url, body, accessToken(forceRefresh = true), headers)
        }.also { touch() }
    }

    // ---- Session ----

    private fun sessionFrom(body: JsonObject): Session {
        val user = body["user"] as? JsonObject
        val access = body["access_token"]?.jsonPrimitive?.contentOrNull
        val refresh = body["refresh_token"]?.jsonPrimitive?.contentOrNull
        val userId = user?.get("id")?.jsonPrimitive?.contentOrNull
        if (access == null || refresh == null || userId == null) {
            throw ApiException("invalid_response", persianError("invalid_response", ErrorKind.Server), ErrorKind.Server)
        }
        val now = System.currentTimeMillis() / 1000
        return Session(
            accessToken = access,
            refreshToken = refresh,
            expiresAt = body["expires_at"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ?: (now + (body["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600)),
            userId = userId,
            email = user["email"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            lastActiveAt = now,
        )
    }

    /** Slides the 180-day window forward at most once an hour, so it is not a write per request. */
    private fun touch() {
        val current = session ?: return
        val now = System.currentTimeMillis() / 1000
        if (now - current.lastActiveAt > 3600) saveSession(current.copy(lastActiveAt = now))
    }

    private suspend fun accessToken(forceRefresh: Boolean = false): String {
        val known = session ?: throw expired()
        if (!forceRefresh && known.expiresAt - REFRESH_MARGIN_SECONDS > System.currentTimeMillis() / 1000) return known.accessToken
        return refreshLock.withLock {
            val current = session ?: throw expired()
            // Another call may have refreshed while this one waited for the lock.
            if (current.accessToken != known.accessToken && current.expiresAt - REFRESH_MARGIN_SECONDS > System.currentTimeMillis() / 1000) {
                return@withLock current.accessToken
            }
            if (store.isExpiredByInactivity(current)) {
                endSession()
                throw expired()
            }
            val body = try {
                withRetry(safeToRetry = true) {
                    execute("POST", "$base/auth/v1/token?grant_type=refresh_token",
                        buildJsonObject { put("refresh_token", current.refreshToken) }, null, emptyMap())
                }.jsonObject
            } catch (error: ApiException) {
                // Network trouble keeps the session; only a rejected refresh token ends it.
                if (error.kind in setOf(ErrorKind.Invalid, ErrorKind.SessionExpired, ErrorKind.NotFound)) {
                    endSession()
                    throw expired()
                }
                throw error
            }
            sessionFrom(body).also(::saveSession).accessToken
        }
    }

    private fun expired() = ApiException("session_expired", persianError("session_expired"), ErrorKind.SessionExpired, retryable = false)

    /** Call when the app comes to the foreground: refreshes early so the first screen is fast. */
    suspend fun keepSessionAlive() {
        if (session != null) runCatching { accessToken() }
    }

    // ---- Auth endpoints ----

    suspend fun signIn(email: String, password: String) {
        val body = public("POST", "$base/auth/v1/token?grant_type=password", buildJsonObject {
            put("email", email); put("password", password)
        }).jsonObject
        saveSession(sessionFrom(body))
    }

    // Email links open the website's auth pages (they are on the Supabase redirect allowlist).
    private fun siteLink(path: String) =
        java.net.URLEncoder.encode("${BuildConfig.SITE_URL.trimEnd('/')}/fa/auth/$path", "UTF-8")

    /** Sends a password-reset email; the link opens the website's update-password page. */
    suspend fun requestPasswordReset(email: String) {
        public("POST", "$base/auth/v1/recover?redirect_to=${siteLink("update-password")}", buildJsonObject { put("email", email) })
    }

    /** Sends the signup confirmation email again. */
    suspend fun resendConfirmation(email: String) {
        public("POST", "$base/auth/v1/resend?redirect_to=${siteLink("verify")}", buildJsonObject {
            put("type", "signup"); put("email", email)
        })
    }

    /** Returns true when the account still needs email confirmation. */
    suspend fun signUp(email: String, password: String): Boolean {
        val body = public("POST", "$base/auth/v1/signup?redirect_to=${siteLink("verify")}", buildJsonObject {
            put("email", email); put("password", password)
        }).jsonObject
        if (body["access_token"] != null) {
            saveSession(sessionFrom(body)); return false
        }
        return true
    }

    /** Revokes this device's refresh token on the server (best effort), then forgets it locally. */
    suspend fun signOut() {
        val token = session?.accessToken
        saveSession(null)
        if (token != null) runCatching { execute("POST", "$base/auth/v1/logout?scope=local", null, token, emptyMap()) }
    }

    // ---- App data ----

    suspend fun function(name: String, body: JsonObject, idempotencyKey: String? = null, readOnly: Boolean = false): JsonObject =
        authed(
            "POST",
            "$base/functions/v1/$name",
            body,
            idempotencyKey?.let { mapOf("Idempotency-Key" to it) } ?: emptyMap(),
            safeToRetry = readOnly || idempotencyKey != null,
        ) as? JsonObject ?: JsonObject(emptyMap())

    suspend fun dashboard(): Dashboard {
        val body = function("account-data", buildJsonObject { put("action", "dashboard") }, readOnly = true)
        val dashboard = body["dashboard"]
            ?: throw ApiException("invalid_response", persianError("invalid_response", ErrorKind.Server), ErrorKind.Server)
        return json.decodeFromJsonElement(Dashboard.serializer(), dashboard)
    }

    private suspend fun mealAction(action: String, localDate: String, slotKey: String, optionKey: String) {
        function("account-data", buildJsonObject {
            put("action", action); put("local_date", localDate); put("slot_key", slotKey); put("option_key", optionKey)
        }, UUID.randomUUID().toString())
    }

    suspend fun completeMeal(localDate: String, slotKey: String, optionKey: String) = mealAction("complete-meal", localDate, slotKey, optionKey)
    suspend fun undoMeal(localDate: String, slotKey: String, optionKey: String) = mealAction("undo-meal", localDate, slotKey, optionKey)
    suspend fun selectMeal(localDate: String, slotKey: String, optionKey: String) = mealAction("select-meal", localDate, slotKey, optionKey)

    /** Daily check-in, same contract as the website (checkins → save-daily). */
    suspend fun saveDailyCheckin(localDate: String, checkin: DailyCheckin) {
        function("checkins", buildJsonObject {
            put("action", "save-daily")
            put("local_date", localDate)
            put("timezone", java.util.TimeZone.getDefault().id ?: "Asia/Tehran")
            put("checkin", json.encodeToJsonElement(DailyCheckin.serializer(), checkin))
        }, UUID.randomUUID().toString())
    }

    // ---- Onboarding (same draft table and server steps as the website) ----

    suspend fun loadDraft(): OnboardingDraft? {
        val userId = session?.userId ?: return null
        val rows = authed("GET", "$base/rest/v1/onboarding_drafts?select=current_step,payload&user_id=eq.$userId", null)
            as? JsonArray ?: return null
        val row = rows.firstOrNull() as? JsonObject ?: return null
        val payload = row["payload"] as? JsonObject ?: JsonObject(emptyMap())
        return OnboardingDraft(
            step = row["current_step"]?.jsonPrimitive?.contentOrNull ?: "basics",
            values = payload.mapValues { (_, value) -> if (value is JsonPrimitive) value.contentOrNull.orEmpty() else value.toString() },
        )
    }

    /** Upsert of the whole draft: repeating it is harmless, so it may retry. */
    suspend fun saveDraft(step: String, values: Map<String, String>) {
        val userId = session?.userId ?: throw expired()
        authed(
            "POST",
            "$base/rest/v1/onboarding_drafts?on_conflict=user_id",
            buildJsonArray {
                add(buildJsonObject {
                    put("user_id", userId)
                    put("current_step", step)
                    put("payload", JsonObject(values.mapValues { JsonPrimitive(it.value) }))
                })
            },
            mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
            safeToRetry = true,
        )
    }

    /** Returns `complete` or `automation_blocked`. */
    suspend fun completeOnboarding(idempotencyKey: String): String {
        val body = function("account-data", buildJsonObject { put("action", "complete-onboarding") }, idempotencyKey)
        return (body["onboarding"] as? JsonObject)?.get("status")?.jsonPrimitive?.contentOrNull ?: "complete"
    }

    /** Starts (or resumes) the monthly plan job. Same idempotency key = same job. */
    suspend fun requestPlanGeneration(idempotencyKey: String): String {
        val body = function("generate-monthly-plan", buildJsonObject { put("locale", "fa-IR") }, idempotencyKey)
        return (body["job"] as? JsonObject)?.get("status")?.jsonPrimitive?.contentOrNull ?: "unknown"
    }

    /**
     * Starts this month's plan: the AI job when it is available, otherwise the starter plan.
     * Returns true when the starter plan was used. Same key = same job, so retrying is safe.
     */
    suspend fun startMonthlyPlan(idempotencyKey: String): Boolean = try {
        startPlanOnce(idempotencyKey)
    } catch (error: ApiException) {
        if (error.code != "managed_plan_path_not_selected") throw error
        // Setup was finished on the website with "I'll bring my own plan". Asking the app for a plan
        // means the Momentum plan: record that choice the same way the website does, then retry.
        selectManagedPlanPath("$idempotencyKey:managed")
        startPlanOnce("$idempotencyKey:m")
    }

    private suspend fun selectManagedPlanPath(idempotencyKey: String) {
        val draft = loadDraft() ?: throw ApiException("onboarding_draft_not_found", persianError("onboarding_draft_not_found"), ErrorKind.Conflict)
        saveDraft(draft.step, draft.values + ("planSource" to "momentum"))
        // Re-running completion re-reads the draft's planSource into the profile.
        completeOnboarding(idempotencyKey)
    }

    private suspend fun startPlanOnce(idempotencyKey: String): Boolean {
        // One call only: every generate call (polls too) counts against a small hourly limit, so
        // progress is followed through the dashboard instead.
        try {
            requestPlanGeneration(idempotencyKey)
            return false
        } catch (error: ApiException) {
            if (error.code !in AI_UNAVAILABLE_CODES) throw error
        }
        // The starter plan is activated at most once per account, so this cannot pile up plans.
        createStarterPlan("$idempotencyKey:starter")
        return true
    }

    /**
     * Activates the deterministic starter plan (catalog-based, no AI). Used when AI generation is
     * switched off on the server, so a new user still gets a working month.
     */
    suspend fun createStarterPlan(idempotencyKey: String) {
        function("account-data", buildJsonObject { put("action", "create-starter-plan") }, idempotencyKey)
    }

    /** Server-side check of a Cafe Bazaar purchase; returns the new membership end. Safe to repeat. */
    suspend fun verifyBazaarPurchase(productId: String, purchaseToken: String): String? {
        val body = function("payments", buildJsonObject {
            put("action", "bazaar-verify"); put("productId", productId); put("purchaseToken", purchaseToken)
        }, readOnly = true)
        return body["period_end"]?.jsonPrimitive?.contentOrNull
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val REFRESH_MARGIN_SECONDS = 120L

        /** AI generation cannot run right now; a user without a plan gets the starter plan instead. */
        private val AI_UNAVAILABLE_CODES = setOf(
            "ai_temporarily_disabled", "ai_circuit_open", "ai_circuit_breaker_unavailable", "rate_limit_exceeded",
        )
    }
}
