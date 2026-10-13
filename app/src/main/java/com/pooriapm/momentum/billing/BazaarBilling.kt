package com.pooriapm.momentum.billing

import androidx.activity.ComponentActivity
import com.pooriapm.momentum.BuildConfig
import com.pooriapm.momentum.data.Api
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
        connection = payment.connect {
            connectionSucceed { onReady() }
            connectionFailed { }
            disconnected { }
        }
    }

    fun disconnect() {
        connection?.disconnect()
    }

    fun buyMonth(onResult: (BillingResult) -> Unit) {
        val userId = api.session?.userId ?: return onResult(BillingResult.Failed("اول وارد حسابت شو."))
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
            val result = runCatching { api.verifyBazaarPurchase(BuildConfig.BAZAAR_PRODUCT_ID, purchaseToken) }
            result.onSuccess { periodEnd ->
                payment.consumeProduct(purchaseToken) {
                    consumeSucceed { }
                    consumeFailed { }
                }
                onResult(BillingResult.Granted(periodEnd))
            }.onFailure {
                onResult(BillingResult.Failed(it.message ?: "تأیید خرید انجام نشد. دوباره برنامه را باز کن تا خرید تکمیل شود."))
            }
        }
    }
}

sealed interface BillingResult {
    data class Granted(val periodEnd: String?) : BillingResult
    data object Cancelled : BillingResult
    data class Failed(val message: String) : BillingResult
}
