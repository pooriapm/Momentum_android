package com.pooriapm.momentum.data

import android.content.Context
import com.pooriapm.momentum.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
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

val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

@Serializable
data class Session(val accessToken: String, val refreshToken: String, val expiresAt: Long, val userId: String, val email: String)

/**
 * Talks to the same Supabase project as the website: GoTrue for sign-in and the
 * Edge Functions (account-data, checkins, generate-monthly-plan, payments) for data.
 */
class Api(context: Context) {
    private val prefs = context.getSharedPreferences("momentum.session", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    var session: Session? = prefs.getString("session", null)?.let { runCatching { json.decodeFromString<Session>(it) }.getOrNull() }
        private set

    private fun store(value: Session?) {
        session = value
        prefs.edit().apply { if (value == null) remove("session") else putString("session", json.encodeToString(Session.serializer(), value)) }.apply()
    }

    private suspend fun post(url: String, body: JsonObject, token: String? = null, idempotent: Boolean = false): JsonObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("apikey", BuildConfig.SUPABASE_ANON_KEY)
                .header("Authorization", "Bearer ${token ?: BuildConfig.SUPABASE_ANON_KEY}")
                .header("X-Momentum-Client", "android")
                .apply { if (idempotent) header("Idempotency-Key", UUID.randomUUID().toString()) }
                .post(body.toString().toRequestBody(jsonType))
                .build()
            val response = try {
                http.newCall(request).execute()
            } catch (error: java.io.IOException) {
                throw ApiException("offline", "اتصال به اینترنت برقرار نیست.")
            }
            response.use {
                val text = it.body?.string().orEmpty()
                val parsed = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: JsonObject(emptyMap())
                if (!it.isSuccessful) {
                    val error = parsed["error"]?.let { e -> runCatching { e.jsonObject }.getOrNull() }
                    val code = error?.get("code")?.jsonPrimitive?.content
                        ?: parsed["error_code"]?.jsonPrimitive?.content
                        ?: parsed["code"]?.jsonPrimitive?.content
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
        val body = post("$base/auth/v1/token?grant_type=password", buildJsonObject {
            put("email", email); put("password", password)
        })
        store(sessionFrom(body))
    }

    /** Returns true when the account still needs email confirmation. */
    suspend fun signUp(email: String, password: String): Boolean {
        val body = post("$base/auth/v1/signup", buildJsonObject {
            put("email", email); put("password", password)
        })
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
            post("$base/auth/v1/token?grant_type=refresh_token", buildJsonObject { put("refresh_token", current.refreshToken) })
        } catch (error: ApiException) {
            if (error.code != "offline") store(null)
            throw error
        }
        return sessionFrom(body).also(::store).accessToken
    }

    suspend fun function(name: String, body: JsonObject, idempotent: Boolean = false): JsonObject =
        post("$base/functions/v1/$name", body, accessToken(), idempotent)

    suspend fun dashboard(): Dashboard {
        val body = function("account-data", buildJsonObject { put("action", "dashboard") })
        return json.decodeFromJsonElement(Dashboard.serializer(), body["dashboard"] as JsonElement)
    }

    suspend fun completeMeal(localDate: String, slotKey: String, optionKey: String) {
        function("account-data", buildJsonObject {
            put("action", "complete-meal"); put("local_date", localDate); put("slot_key", slotKey); put("option_key", optionKey)
        }, idempotent = true)
    }

    suspend fun undoMeal(localDate: String, slotKey: String, optionKey: String) {
        function("account-data", buildJsonObject {
            put("action", "undo-meal"); put("local_date", localDate); put("slot_key", slotKey); put("option_key", optionKey)
        }, idempotent = true)
    }

    /** Server-side check of a Cafe Bazaar purchase; returns the new membership end. */
    suspend fun verifyBazaarPurchase(productId: String, purchaseToken: String): String? {
        val body = function("payments", buildJsonObject {
            put("action", "bazaar-verify"); put("productId", productId); put("purchaseToken", purchaseToken)
        })
        return body["period_end"]?.jsonPrimitive?.content
    }
}

private fun persianError(code: String): String = when (code) {
    "invalid_credentials", "invalid_grant" -> "ایمیل یا رمز عبور درست نیست."
    "email_not_confirmed" -> "اول ایمیلت را تأیید کن؛ لینک تأیید برایت فرستاده شده است."
    "user_already_exists" -> "با این ایمیل قبلاً حساب ساخته شده است. وارد شو."
    "weak_password" -> "رمز عبور باید دست‌کم ۸ نویسه باشد."
    "PAYMENTS_DISABLED" -> "پرداخت هنوز فعال نشده است."
    "purchase_owned_elsewhere" -> "این خرید متعلق به حساب دیگری است."
    "purchase_not_found", "purchase_refunded" -> "کافه‌بازار این خرید را تأیید نکرد."
    "rate_limited" -> "درخواست‌ها زیاد شد؛ کمی بعد دوباره امتحان کن."
    else -> "کار انجام نشد. کمی بعد دوباره امتحان کن."
}
