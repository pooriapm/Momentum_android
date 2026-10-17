package com.pooriapm.momentum.billing

import androidx.activity.ComponentActivity
import com.pooriapm.momentum.BuildConfig
import com.pooriapm.momentum.data.Api
import com.pooriapm.momentum.data.asApi
import ir.cafebazaar.poolakey.Connection
import ir.cafebazaar.poolakey.Payment
import ir.cafebazaar.poolakey.config.PaymentConfiguration
import ir.cafebazaar.poolakey.config.SecurityCheck
import ir.cafebazaar.poolakey.request.PurchaseRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One consumable product = 30 days of membership. Flow:
 * buy in Bazaar → server validates the token with Bazaar and grants the period → consume.
 * Consuming only after the server grant means a dropped connection never loses a payment:
 * the unconsumed purchase is retried by [recoverPendingPurchases].
 */
class BazaarBilling(private val activity: ComponentActivity, private val api: Api, private val scope: CoroutineScope) {
    private val payment = Payment(
        context = activity,
        config = PaymentConfiguration(localSecurityCheck = SecurityCheck.Enable(rsaPublicKey = BuildConfig.BAZAAR_RSA_KEY)),
    )
    private var connection: Connection? = null

    fun connect(onReady: () -> Unit = {}) {
        if (BuildConfig.BAZAAR_RSA_KEY.isBlank()) return
        connection = runCatching { payment.connect {
            connectionSucceed { onReady() }
            connectionFailed { }
            disconnected { }
        } }.getOrNull()
    }

    fun disconnect() {
        connection?.disconnect()
    }

    fun buyMonth(onResult: (BillingResult) -> Unit) {
        val userId = api.session?.userId ?: return onResult(BillingResult.Failed("اول وارد حسابت شو."))
        if (BuildConfig.BAZAAR_RSA_KEY.isBlank()) {
            return onResult(BillingResult.Failed("خرید در این نسخه فعال نیست (کلید کافه‌بازار تنظیم نشده)."))
        }
        // Poolakey can throw if the Bazaar connection is gone; that must be a message, not a crash.
        try {
            startPurchase(userId, onResult)
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            onResult(BillingResult.Failed("کافه‌بازار باز نشد. مطمئن شو برنامه کافه‌بازار نصب و به‌روز است."))
        }
    }

    private fun startPurchase(userId: String, onResult: (BillingResult) -> Unit) {
        payment.purchaseProduct(
            registry = activity.activityResultRegistry,
            request = PurchaseRequest(productId = BuildConfig.BAZAAR_PRODUCT_ID, payload = userId),
        ) {
            purchaseFlowBegan { }
            failedToBeginFlow { onResult(BillingResult.Failed("کافه‌بازار باز نشد. مطمئن شو برنامه کافه‌بازار نصب و به‌روز است.")) }
            purchaseCanceled { onResult(BillingResult.Cancelled) }
            purchaseFailed { onResult(BillingResult.Failed("خرید انجام نشد.")) }
            purchaseSucceed { purchase -> confirm(purchase.purchaseToken, onResult) }
        }
    }

    /** Finishes purchases that were paid but not yet granted (e.g. the app closed mid-way). */
    fun recoverPendingPurchases(onGranted: () -> Unit) {
        if (BuildConfig.BAZAAR_RSA_KEY.isBlank()) return
        runCatching { queryPurchases(onGranted) }
    }

    private fun queryPurchases(onGranted: () -> Unit) {
        payment.getPurchasedProducts {
            querySucceed { purchases ->
                purchases.filter { it.productId == BuildConfig.BAZAAR_PRODUCT_ID }.forEach { purchase ->
                    confirm(purchase.purchaseToken) { if (it is BillingResult.Granted) onGranted() }
                }
            }
            queryFailed { }
        }
    }

    private fun confirm(purchaseToken: String, onResult: (BillingResult) -> Unit) {
        scope.launch {
            val periodEnd = try {
                api.verifyBazaarPurchase(BuildConfig.BAZAAR_PRODUCT_ID, purchaseToken)
            } catch (raw: Exception) {
                val error = raw.asApi()
                // Not consumed: the purchase stays in Bazaar and is verified again on the next launch.
                val hint = if (error.retryable) " خرید محفوظ است و دفعه بعد که اپ را باز کنی دوباره بررسی می‌شود." else ""
                onResult(BillingResult.Failed(error.message.orEmpty() + hint))
                return@launch
            }
            runCatching {
                payment.consumeProduct(purchaseToken) {
                    consumeSucceed { }
                    consumeFailed { }
                }
            }
            onResult(BillingResult.Granted(periodEnd))
        }
    }
}

sealed interface BillingResult {
    data class Granted(val periodEnd: String?) : BillingResult
    data object Cancelled : BillingResult
    data class Failed(val message: String) : BillingResult
}
