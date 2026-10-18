package com.pooriapm.momentum.data

import com.pooriapm.momentum.BuildConfig
import kotlinx.coroutines.CancellationException

/** What went wrong, independent of the exact server code. Drives retries and the UI. */
enum class ErrorKind {
    Offline,
    Timeout,
    /** The session cannot be refreshed any more; the user has to sign in again. */
    SessionExpired,
    /** Signed in, but this account may not use the feature (closed alpha, membership…). */
    NoAccess,
    NotFound,
    Conflict,
    RateLimited,
    /** The request was understood but its data was rejected (4xx validation). */
    Invalid,
    /** 5xx or a response the app could not read. */
    Server,
    NotConfigured,
}

/**
 * Error shown to the user. [code] is the server's stable error code when there is one,
 * [retryable] says whether "try again" can help, [retryAfterSeconds] comes from Retry-After.
 */
class ApiException(
    val code: String,
    message: String,
    val kind: ErrorKind = ErrorKind.Server,
    val retryable: Boolean = kind in setOf(ErrorKind.Offline, ErrorKind.Timeout, ErrorKind.RateLimited, ErrorKind.Server) &&
        code !in NON_RETRYABLE_CODES,
    val retryAfterSeconds: Long? = null,
    /** The server's own (English, non-personal) explanation; for debug logs only, never shown. */
    val detail: String? = null,
) : Exception(message)

/**
 * Any failure as an [ApiException], so a malformed response or a missing field shows a message
 * instead of closing the app. Coroutine cancellation is rethrown, never swallowed.
 */
fun Throwable.asApi(): ApiException {
    if (this is CancellationException) throw this
    if (BuildConfig.DEBUG) {
        // Codes and exception types only; never request bodies, tokens or personal data.
        val cause = (this as? ApiException)?.let { "${it.kind}/${it.code}" + it.detail?.let { d -> " (${d.take(160)})" }.orEmpty() } ?: "${this::class.java.simpleName}: ${message.orEmpty().take(200)}"
        android.util.Log.w("MomentumApi", "request failed: $cause")
    }
    return this as? ApiException ?: ApiException("invalid_response", persianError("invalid_response"), ErrorKind.Server)
}

fun kindForStatus(status: Int, code: String): ErrorKind = when {
    code in NO_ACCESS_CODES -> ErrorKind.NoAccess
    status == 401 -> ErrorKind.SessionExpired
    status == 403 -> ErrorKind.NoAccess
    status == 404 -> ErrorKind.NotFound
    status == 408 -> ErrorKind.Timeout
    status == 409 -> ErrorKind.Conflict
    status == 429 -> ErrorKind.RateLimited
    status in 400..499 -> ErrorKind.Invalid
    else -> ErrorKind.Server
}

/** 5xx answers that describe a switched-off feature: repeating the call cannot help. */
val NON_RETRYABLE_CODES = setOf(
    "ai_temporarily_disabled", "ai_circuit_open", "PAYMENTS_DISABLED", "not_configured",
    // Per-user limits with long windows (e.g. 3 plans an hour): an immediate retry only burns the quota.
    "rate_limit_exceeded",
)

private val NO_ACCESS_CODES = setOf(
    "ALPHA_DISABLED", "ALPHA_NOT_INVITED", "PUBLIC_BETA_DISABLED",
    "entitlement_required", "SUBSCRIPTION_INACTIVE", "PAYMENT_METHOD_REQUIRED", "quota_exceeded",
)

/** Persian copy for server codes; falls back to a message per [ErrorKind]. */
fun persianError(code: String, kind: ErrorKind? = null): String = when (code) {
    "invalid_credentials", "invalid_grant" -> "ایمیل یا رمز عبور درست نیست."
    "email_not_confirmed", "email_confirmation_required" -> "اول ایمیلت را تأیید کن؛ لینک تأیید برایت فرستاده شده است."
    "user_already_exists", "email_exists" -> "با این ایمیل قبلاً حساب ساخته شده است. وارد شو."
    "weak_password" -> "رمز عبور ضعیف است؛ حداقل ۸ کاراکتر و ترکیبی از حرف و عدد بگذار."
    "validation_failed", "email_address_invalid" -> "ایمیل معتبر نیست."
    "over_email_send_rate_limit", "over_request_rate_limit", "rate_limited", "rate_limit_exceeded" -> "درخواست‌ها زیاد شد؛ چند دقیقه بعد دوباره امتحان کن."
    "consent_update_required" -> "نسخه تازه شرایط و حریم خصوصی منتشر شده؛ دوباره رضایت‌ها را تأیید کن."
    "onboarding_draft_invalid" -> "بعضی پاسخ‌ها ناقص است. مرور را بررسی کن."
    "onboarding_draft_not_found" -> "پاسخ‌های راه‌اندازی پیدا نشد؛ یک بار دیگر از مرور تأیید کن."
    "idempotency_key_reused" -> "این درخواست قبلاً با اطلاعات دیگری ثبت شده بود؛ صفحه را تازه کن."
    "PAYMENTS_DISABLED" -> "پرداخت هنوز فعال نشده است."
    "purchase_owned_elsewhere" -> "این خرید متعلق به حساب دیگری است."
    "purchase_not_found", "purchase_refunded" -> "کافه‌بازار این خرید را تأیید نکرد."
    "ALPHA_DISABLED", "ALPHA_NOT_INVITED", "PUBLIC_BETA_DISABLED" ->
        "حسابت ساخته شده، ولی Momentum فعلاً فقط برای افراد دعوت‌شده باز است. وقتی دسترسی‌ات فعال شد، همین‌جا ادامه می‌دهی."
    "entitlement_required", "PAYMENT_METHOD_REQUIRED", "quota_exceeded" -> "برای این کار عضویت فعال لازم است؛ از «حساب من» فعالش کن."
    "completed_meal_locked" -> "این وعده ثبت شده است؛ برای تغییرش اول «حذف ثبت» را بزن."
    "meal_not_completed" -> "این وعده ثبتی برای حذف ندارد؛ صفحه را تازه کن."
    "local_date_mismatch" -> "روز عوض شده است؛ برنامه امروز را دوباره بارگذاری کردیم."
    "plan_option_not_found" -> "این گزینه دیگر در برنامه نیست؛ صفحه را تازه کن."
    "shopping_purchase_limit_reached" -> "تعداد ثبت‌های خرید این برنامه به سقف رسیده است."
    "shopping_purchase_not_found" -> "این خرید پیدا نشد؛ فهرست را تازه کن."
    "active_plan_not_found" -> "برنامه فعالی پیدا نشد؛ صفحه را تازه کن."
    "workout_date_not_current" -> "تمرین فقط برای امروز ثبت می‌شود؛ صفحه را تازه کن."
    "active_workout_not_found" -> "تمرین امروز در برنامه پیدا نشد؛ صفحه را تازه کن."
    "workout_session_closed" -> "این جلسه تمرین بسته شده است."
    "workout_session_not_found" -> "جلسه تمرین پیدا نشد؛ صفحه را تازه کن."
    "exercise_has_no_completed_sets" -> "اول دست‌کم یک ست را تیک بزن."
    "unfinished_exercises" -> "هنوز حرکتی مانده؛ انجامش بده یا ردش کن."
    "invalid_catalog_substitution" -> "این حرکت جایگزین مجاز نیست."
    "skip_reason_required", "stop_reason_required" -> "یک دلیل کوتاه انتخاب کن."
    "invalid_pain_report" -> "شدت و محل درد را مشخص کن."
    "invalid_settings" -> "بعضی مقدارها معتبر نیست؛ دوباره بررسی کن."
    "account_settings_not_ready" -> "اول راه‌اندازی حساب را کامل کن."
    "settings_unavailable", "settings_update_failed" -> "تنظیمات الان ذخیره نشد؛ کمی بعد دوباره امتحان کن."
    "next_cycle_period_unavailable" -> "یادداشت ماه بعد هنوز برای برنامه تو فعال نیست."
    "invalid_next_cycle_note" -> "یادداشت حداکثر ۵۰۰ کاراکتر است."
    "starter_plan_profile_incomplete" -> "اول راه‌اندازی حساب را کامل کن."
    "SUBSCRIPTION_INACTIVE" -> "برای ماه دوم به بعد عضویت فعال لازم است؛ از «حساب من» عضویت را فعال کن و دوباره بساز."
    "unsupported_action" -> "این قابلیت هنوز روی سرور فعال نشده است."
    "ai_temporarily_disabled" -> "ساخت هوشمند برنامه فعلاً در دسترس نیست."
    "session_expired" -> "مدتی است وارد نشده‌ای؛ برای امنیت دوباره وارد شو."
    "not_configured" -> "این نسخه از اپ به سرور وصل نیست (momentum.properties خالی بود)."
    else -> when (kind) {
        ErrorKind.Offline -> "اتصال به اینترنت برقرار نیست. وصل که شدی دوباره امتحان کن."
        ErrorKind.Timeout -> "پاسخ سرور طول کشید. دوباره امتحان کن."
        ErrorKind.SessionExpired -> "مدتی است وارد نشده‌ای؛ برای امنیت دوباره وارد شو."
        ErrorKind.NoAccess -> "این بخش برای حسابت فعال نیست."
        ErrorKind.NotFound -> "چیزی که دنبالش بودی پیدا نشد؛ صفحه را تازه کن."
        ErrorKind.Conflict -> "این اطلاعات همین الان تغییر کرده؛ صفحه را تازه کن و دوباره امتحان کن."
        ErrorKind.RateLimited -> "درخواست‌ها زیاد شد؛ کمی بعد دوباره امتحان کن."
        ErrorKind.Invalid -> "اطلاعات فرستاده‌شده درست نبود. دوباره بررسی کن."
        ErrorKind.NotConfigured -> "این نسخه از اپ به سرور وصل نیست."
        else -> "مشکلی در سرور پیش آمد. کمی بعد دوباره امتحان کن."
    }
}
