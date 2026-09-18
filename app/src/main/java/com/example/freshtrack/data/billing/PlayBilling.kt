package com.example.freshtrack.data.billing

import android.app.Activity
import kotlinx.coroutines.flow.Flow

/**
 * The one subscription this app sells, as configured in Play Console. The
 * Cloud Function keeps the same allow-list; a purchase of anything else is
 * refused there even if it reached here.
 */
const val PREMIUM_PRODUCT_ID = "premium"

/**
 * Whether Premium can be reached from the UI at all. Off until the Play
 * product exists and a purchase has been proven end to end on the internal
 * testing track; with it off the Settings card behaves as it did before the
 * billing client existed and nothing talks to Play Billing.
 */
const val PREMIUM_ON_SALE = false

/**
 * One way to pay for premium: a base plan of the [PREMIUM_PRODUCT_ID]
 * subscription, priced by Play in the user's own currency. The app never
 * formats a price itself.
 */
data class PremiumPlan(
    /** `monthly` or `annual`. */
    val basePlanId: String,
    /** Exactly as Play renders it, for example `$3.99` or `£2.99`. */
    val formattedPrice: String,
    /** ISO 8601 duration, `P1M` or `P1Y`. */
    val billingPeriod: String,
    /** Opaque; handed back to Play to start the purchase. */
    val offerToken: String
)

/** A purchase Play currently holds for this user and this app. */
data class OwnedPurchase(
    val purchaseToken: String,
    val productIds: List<String>,
    /** False until the server has verified it. Play refunds after three days if it stays false. */
    val isAcknowledged: Boolean
) {
    val isPremium: Boolean get() = PREMIUM_PRODUCT_ID in productIds
}

/** What Play reports back after the purchase sheet closes. */
sealed interface PurchaseUpdate {
    data class Purchased(val purchases: List<OwnedPurchase>) : PurchaseUpdate
    /** The user backed out. Not an error. */
    data object Cancelled : PurchaseUpdate
    /** Play already holds a subscription for this user; present it to the server instead. */
    data object AlreadyOwned : PurchaseUpdate
    data class Failed(val message: String) : PurchaseUpdate
}

/**
 * Play Billing, narrowed to what this app does with it, so the purchase
 * flow can be tested on the JVM with Play faked. The real implementation is
 * [PlayBillingClient].
 */
interface PlayBilling {
    /** The base plans of the premium subscription, with Play's prices. */
    suspend fun premiumPlans(): Result<List<PremiumPlan>>

    /** Every subscription purchase Play holds for this user and app. */
    suspend fun ownedPurchases(): Result<List<OwnedPurchase>>

    /**
     * Opens Play's purchase sheet. Returns as soon as the sheet is showing;
     * the outcome arrives on [purchaseUpdates]. [premiumPlans] must have been
     * loaded first — Play wants the product it described, not just the id.
     */
    fun subscribe(activity: Activity, plan: PremiumPlan): Result<Unit>

    val purchaseUpdates: Flow<PurchaseUpdate>
}
