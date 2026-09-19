package com.example.freshtrack.data.billing

import android.app.Activity

/**
 * The one entitlement this app sells, as configured in the RevenueCat
 * dashboard. The webhook function keeps the same name; a purchase that
 * grants anything else is not premium there either.
 */
const val PREMIUM_ENTITLEMENT_ID = "premium"

/**
 * Whether Premium can be reached from the UI at all. Off until the products
 * exist in the stores and a purchase has been proven end to end on the
 * internal testing track; with it off the Settings card behaves as it did
 * before the store client existed and nothing talks to RevenueCat.
 */
const val PREMIUM_ON_SALE = false

/**
 * One way to pay for premium: a package of the current offering, priced by
 * the store in the user's own currency. The app never formats a price itself.
 */
data class PremiumPlan(
    /** `monthly` or `annual`. */
    val basePlanId: String,
    /** Exactly as the store renders it, for example `$3.99` or `£2.99`. */
    val formattedPrice: String,
    /** ISO 8601 duration, `P1M` or `P1Y`. */
    val billingPeriod: String,
    /** RevenueCat's package identifier; handed back to start the purchase. */
    val packageId: String
)

/** What the store reports after its purchase sheet closes. */
sealed interface PurchaseOutcome {
    /** Paid, and RevenueCat already shows the entitlement active. */
    data object Purchased : PurchaseOutcome
    /** The store is still waiting on the payment. The webhook lands when it clears. */
    data object Pending : PurchaseOutcome
    /** The user backed out. Not an error. */
    data object Cancelled : PurchaseOutcome
}

/**
 * The store, narrowed to what this app does with it, so the purchase flow
 * can be tested on the JVM with the store faked. The real implementation is
 * [RevenueCatStore]. Nothing here decides that the account is premium: the
 * server hears from RevenueCat and writes the flag, and the app reads that.
 */
interface PremiumStore {
    /** The plans of the current offering, with the store's prices. */
    suspend fun premiumPlans(): Result<List<PremiumPlan>>

    /**
     * Opens the store's purchase sheet and waits for it to close.
     * [premiumPlans] must have been loaded first.
     */
    suspend fun subscribe(activity: Activity, plan: PremiumPlan): Result<PurchaseOutcome>

    /** Asks the store for anything this account already owns. True if premium is active. */
    suspend fun restore(): Result<Boolean>
}
