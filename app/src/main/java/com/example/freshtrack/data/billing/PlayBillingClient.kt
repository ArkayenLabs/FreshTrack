package com.example.freshtrack.data.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume

class BillingException(val code: Int, message: String) : Exception("$message (Play Billing $code)")

/**
 * The real [PlayBilling], over Billing Library 8.
 *
 * One client for the process lifetime. Connection is lazy and the library
 * reconnects on its own after a drop, so callers never see connection state:
 * every call waits for readiness first. Nothing here acknowledges a purchase
 * — that is the server's job after it has verified the token, so an
 * unverified purchase is refunded by Play rather than kept for free.
 */
class PlayBillingClient(context: Context) : PlayBilling, PurchasesUpdatedListener {

    private val updates = MutableSharedFlow<PurchaseUpdate>(extraBufferCapacity = 4)
    override val purchaseUpdates: Flow<PurchaseUpdate> = updates

    private val client: BillingClient = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val connecting = Mutex()

    /** The product as Play described it, kept because [subscribe] must hand it back. */
    @Volatile
    private var premiumDetails: ProductDetails? = null

    private suspend fun ready(): Result<Unit> {
        if (client.isReady) return Result.success(Unit)
        return connecting.withLock {
            if (client.isReady) return Result.success(Unit)
            suspendCancellableCoroutine { cont ->
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        if (cont.isActive) cont.resume(result.asResult())
                    }

                    override fun onBillingServiceDisconnected() {
                        // The library reconnects; the next call waits on isReady again.
                    }
                })
            }
        }
    }

    override suspend fun premiumPlans(): Result<List<PremiumPlan>> = ready().mapCatching {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(
                listOf(
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PREMIUM_PRODUCT_ID)
                        .setProductType(BillingClient.ProductType.SUBS)
                        .build()
                )
            )
            .build()
        val details = suspendCancellableCoroutine<List<ProductDetails>> { cont ->
            client.queryProductDetailsAsync(params) { result, query ->
                if (!cont.isActive) return@queryProductDetailsAsync
                result.asResult()
                    .onSuccess { cont.resume(query.productDetailsList) }
                    .onFailure { cont.resumeWith(Result.failure(it)) }
            }
        }
        val premium = details.firstOrNull { it.productId == PREMIUM_PRODUCT_ID }
            ?: throw BillingException(BillingClient.BillingResponseCode.ITEM_UNAVAILABLE, "Premium is not on sale here")
        premiumDetails = premium
        premium.subscriptionOfferDetails.orEmpty()
            // A base plan's own offer has no offerId; introductory offers do.
            // Only base plans are configured, and only base plans are shown.
            .filter { it.offerId == null }
            .map { offer ->
                // The last pricing phase is the recurring one.
                val phase = offer.pricingPhases.pricingPhaseList.last()
                PremiumPlan(
                    basePlanId = offer.basePlanId,
                    formattedPrice = phase.formattedPrice,
                    billingPeriod = phase.billingPeriod,
                    offerToken = offer.offerToken
                )
            }
    }

    override suspend fun ownedPurchases(): Result<List<OwnedPurchase>> = ready().mapCatching {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.SUBS)
            .build()
        suspendCancellableCoroutine { cont ->
            client.queryPurchasesAsync(params) { result, purchases ->
                if (!cont.isActive) return@queryPurchasesAsync
                result.asResult()
                    .onSuccess { cont.resume(purchases.owned()) }
                    .onFailure { cont.resumeWith(Result.failure(it)) }
            }
        }
    }

    override fun subscribe(activity: Activity, plan: PremiumPlan): Result<Unit> {
        val details = premiumDetails
            ?: return Result.failure(IllegalStateException("premiumPlans() has not been loaded"))
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(plan.offerToken)
                        .build()
                )
            )
            .build()
        return client.launchBillingFlow(activity, params).asResult()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        val update = when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> PurchaseUpdate.Purchased(purchases.orEmpty().owned())
            BillingClient.BillingResponseCode.USER_CANCELED -> PurchaseUpdate.Cancelled
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> PurchaseUpdate.AlreadyOwned
            else -> PurchaseUpdate.Failed(result.debugMessage.ifBlank { "Play Billing ${result.responseCode}" })
        }
        updates.tryEmit(update)
    }

    /**
     * Only completed purchases. A PENDING one (a slow payment method) is
     * reported again by Play once it clears, and [ownedPurchases] on resume
     * picks it up then.
     */
    private fun List<Purchase>.owned(): List<OwnedPurchase> =
        filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
            .map { OwnedPurchase(it.purchaseToken, it.products, it.isAcknowledged) }

    private fun BillingResult.asResult(): Result<Unit> =
        if (responseCode == BillingClient.BillingResponseCode.OK) Result.success(Unit)
        else Result.failure(BillingException(responseCode, debugMessage.ifBlank { "Play Billing error" }))
}
