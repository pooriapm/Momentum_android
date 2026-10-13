package com.pooriapm.momentum.data

import android.content.Context
import com.pooriapm.momentum.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Error shown to the user; [code] is the server's stable error code when there is one. */
class ApiException(val code: String, message: String) : Exception(message)

val json = Json { ignoreUnknownKeys = true; explicitNulls = false; encodeDefaults = true }

@Serializable
data class Session(val accessToken: String, val refreshToken: String, val expiresAt: Long, val userId: String, val email: String)

data class OnboardingDraft(val step: String, val values: Map<String, String>)

/**
 * Talks to the same Supabase project as the website: GoTrue for sign-in, PostgREST for the
 * onboarding draft (row-level security keeps it per user) and the Edge Functions for the rest.
 */
class Api(context: Context) {
    private val prefs = context.getSharedPreferences("momentum.session", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    var session: Session? = prefs.getString("session", null)?.let { runCatching { json.decodeFromString<Session>(it) }.getOrNull() }
        private set

    private fun store(value: Session?) {
        session = value
        prefs.edit().apply { if (value == null) remove("session") else putString("session", json.encodeToString(Session.serializer(), value)) }.apply()
    }

    private suspend fun send(
        method: String,
        url: String,
        body: JsonElement?,
        token: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): JsonElement = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
            .header("Authorization", "Bearer ${token ?: BuildConfig.SUPABASE_ANON_KEY}")
            .header("X-Momentum-Client", "android")
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .method(method, body?.toString()?.toRequestBody(jsonType))
            .build()
        val response = try {
            http.newCall(request).execute()
        } catch (error: java.io.IOException) {
            throw ApiException("offline", "اتصال به اینترنت برقرار نیست.")
        }
        response.use {
            val text = it.body?.string().orEmpty()
            val parsed = runCatching { json.parseToJsonElement(text) }.getOrNull() ?: JsonObject(emptyMap())
            if (!it.isSuccessful) {
                val obj = parsed as? JsonObject ?: JsonObject(emptyMap())
                val error = obj["error"] as? JsonObject
                val code = error?.get("code")?.jsonPrimitive?.contentOrNull
                    ?: obj["error_code"]?.jsonPrimitive?.contentOrNull
                    ?: (obj["code"] as? JsonPrimitive)?.contentOrNull
                    ?: "http_${it.code}"
                throw ApiException(code, persianError(code))
            }
            parsed
        }
    }

    private fun sessionFrom(body: JsonObject): Session {
        val user = body["user"]!!.jsonObject
        return Session(
            accessToken = body["access_token"]!!.jsonPrimitive.content,
            refreshToken = body["refresh_token"]!!.jsonPrimitive.content,
            expiresAt = System.currentTimeMillis() / 1000 + (body["expires_in"]?.jsonPrimitive?.content?.toLongOrNull() ?: 3600),
            userId = user["id"]!!.jsonPrimitive.content,
            email = user["email"]?.jsonPrimitive?.content.orEmpty(),
        )
    }

    suspend fun signIn(email: String, password: String) {
        val body = send("POST", "$base/auth/v1/token?grant_type=password", buildJsonObject {
            put("email", email); put("password", password)
        }).jsonObject
        store(sessionFrom(body))
    }

    /** Returns true when the account still needs email confirmation. */
    suspend fun signUp(email: String, password: String): Boolean {
        val body = send("POST", "$base/auth/v1/signup", buildJsonObject {
            put("email", email); put("password", password)
        }).jsonObject
        if (body["access_token"] != null) {
            store(sessionFrom(body)); return false
        }
        return true
    }

    fun signOut() = store(null)

    private suspend fun accessToken(): String {
        val current = session ?: throw ApiException("authentication_required", "دوباره وارد شو.")
        if (current.expiresAt - 60 > System.currentTimeMillis() / 1000) return current.accessToken
        val body = try {
            send("POST", "$base/auth/v1/token?grant_type=refresh_token", buildJsonObject { put("refresh_token", current.refreshToken) }).jsonObject
        } catch (error: ApiException) {
            if (error.code != "offline") store(null)
            throw error
        }
        return sessionFrom(body).also(::store).accessToken
    }

    suspend fun function(name: String, body: JsonObject, idempotencyKey: String? = null): JsonObject =
        send(
            "POST",
            "$base/functions/v1/$name",
            body,
            accessToken(),
            idempotencyKey?.let { mapOf("Idempotency-Key" to it) } ?: emptyMap(),
        ).jsonObject

    suspend fun dashboard(): Dashboard {
        val body = function("account-data", buildJsonObject { put("action", "dashboard") })
        return json.decodeFromJsonElement(Dashboard.serializer(), body["dashboard"] as JsonElement)
    }

    suspend fun completeMeal(localDate: String, slotKey: String, optionKey: String) {
        function("account-data", buildJsonObject {
            put("action", "complete-meal"); put("local_date", localDate); put("slot_key", slotKey); put("option_key", optionKey)
        }, UUID.randomUUID().toString())
    }

    suspend fun undoMeal(localDate: String, slotKey: String, optionKey: String) {
        function("account-data", buildJsonObject {
            put("action", "undo-meal"); put("local_date", localDate); put("slot_key", slotKey); put("option_key", optionKey)
        }, UUID.randomUUID().toString())
    }

    suspend fun selectMeal(localDate: String, slotKey: String, optionKey: String) {
        function("account-data", buildJsonObject {
            put("action", "select-meal"); put("local_date", localDate); put("slot_key", slotKey); put("option_key", optionKey)
        }, UUID.randomUUID().toString())
    }

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
        val rows = send(
            "GET",
            "$base/rest/v1/onboarding_drafts?select=current_step,payload&user_id=eq.$userId",
            null,
            accessToken(),
        ) as? JsonArray ?: return null
        val row = rows.firstOrNull() as? JsonObject ?: return null
        val payload = row["payload"] as? JsonObject ?: JsonObject(emptyMap())
        return OnboardingDraft(
            step = row["current_step"]?.jsonPrimitive?.contentOrNull ?: "basics",
            values = payload.mapValues { (_, value) -> (value as? JsonPrimitive)?.contentOrNull.orEmpty() },
        )
    }

    suspend fun saveDraft(step: String, values: Map<String, String>) {
        val userId = session?.userId ?: throw ApiException("authentication_required", "دوباره وارد شو.")
        send(
            "POST",
            "$base/rest/v1/onboarding_drafts?on_conflict=user_id",
            buildJsonArray {
                add(buildJsonObject {
                    put("user_id", userId)
                    put("current_step", step)
                    put("payload", JsonObject(values.mapValues { JsonPrimitive(it.value) }))
                })
            },
            accessToken(),
            mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
        )
    }

    /** Returns `complete` or `automation_blocked`. */
    suspend fun completeOnboarding(idempotencyKey: String): String {
        val body = function("account-data", buildJsonObject { put("action", "complete-onboarding") }, idempotencyKey)
        return body["onboarding"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull ?: "complete"
    }

    /** Starts (or resumes) the monthly plan job. Same idempotency key = same job. */
    suspend fun requestPlanGeneration(idempotencyKey: String): String {
        val body = function("generate-monthly-plan", buildJsonObject { put("locale", "fa-IR") }, idempotencyKey)
        return body["job"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull ?: "unknown"
    }

    /** Server-side check of a Cafe Bazaar purchase; returns the new membership end. */
    suspend fun verifyBazaarPurchase(productId: String, purchaseToken: String): String? {
        val body = function("payments", buildJsonObject {
            put("action", "bazaar-verify"); put("productId", productId); put("purchaseToken", purchaseToken)
        })
        return body["period_end"]?.jsonPrimitive?.contentOrNull
    }
}

private fun persianError(code: String): String = when (code) {
    "invalid_credentials", "invalid_grant" -> "ایمیل یا رمز عبور درست نیست."
    "email_not_confirmed", "email_confirmation_required" -> "اول ایمیلت را تأیید کن؛ لینک تأیید برایت فرستاده شده است."
    "user_already_exists" -> "با این ایمیل قبلاً حساب ساخته شده است. وارد شو."
    "weak_password" -> "رمز عبور باید دست‌کم ۸ نویسه باشد."
    "consent_update_required" -> "نسخه تازه شرایط و حریم خصوصی منتشر شده؛ دوباره رضایت‌ها را تأیید کن."
    "onboarding_draft_invalid" -> "بعضی پاسخ‌ها ناقص است. مرور را بررسی کن."
    "PAYMENTS_DISABLED" -> "پرداخت هنوز فعال نشده است."
    "purchase_owned_elsewhere" -> "این خرید متعلق به حساب دیگری است."
    "purchase_not_found", "purchase_refunded" -> "کافه‌بازار این خرید را تأیید نکرد."
    "rate_limited" -> "درخواست‌ها زیاد شد؛ کمی بعد دوباره امتحان کن."
    "offline" -> "اتصال به اینترنت برقرار نیست."
    else -> "کار انجام نشد. کمی بعد دوباره امتحان کن."
}
