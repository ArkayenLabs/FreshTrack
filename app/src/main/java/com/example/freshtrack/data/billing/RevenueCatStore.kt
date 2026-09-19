package com.example.freshtrack.data.billing

import android.app.Activity
import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.revenuecat.purchases.Offering
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesErrorCode
import com.revenuecat.purchases.PurchasesTransactionException
import com.revenuecat.purchases.awaitOfferings
import com.revenuecat.purchases.awaitPurchase
import com.revenuecat.purchases.awaitRestore

/**
 * The real [PremiumStore], over the RevenueCat SDK, which carries its own
 * Play Billing underneath.
 *
 * Configured once for the process with the signed-in uid as the app user id,
 * and kept in step with Firebase Auth from then on: sign in logs the uid in,
 * sign out logs it out, so a purchase is always RevenueCat's record of a
 * Firebase account and the webhook can find the kitchen to flag. With no
 * SDK key in the build nothing is configured and every call fails; Premium
 * is hidden in that build anyway.
 */
class RevenueCatStore(
    context: Context,
    apiKey: String,
    auth: FirebaseAuth
) : PremiumStore {

    private val purchases: Purchases? = apiKey.takeIf { it.isNotBlank() }?.let { key ->
        Purchases.configure(
            PurchasesConfiguration.Builder(context, key)
                .appUserID(auth.currentUser?.uid)
                .build()
        )
    }

    init {
        purchases?.let { sdk ->
            auth.addAuthStateListener { firebase ->
                val uid = firebase.currentUser?.uid
                when {
                    uid != null && sdk.appUserID != uid -> sdk.logIn(uid)
                    uid == null && !sdk.isAnonymous -> sdk.logOut()
                }
            }
        }
    }

    /** The offering as RevenueCat described it, kept because [subscribe] must hand a package back. */
    @Volatile
    private var offering: Offering? = null

    private fun sdk(): Purchases = purchases ?: error("RevenueCat is not configured in this build")

    override suspend fun premiumPlans(): Result<List<PremiumPlan>> = runCatching {
        val current = sdk().awaitOfferings().current ?: error("RevenueCat has no current offering")
        offering = current
        current.availablePackages.map { it.toPlan() }
    }

    override suspend fun subscribe(activity: Activity, plan: PremiumPlan): Result<PurchaseOutcome> = runCatching {
        val pkg = offering?.availablePackages?.firstOrNull { it.identifier == plan.packageId }
            ?: error("Plans have not been loaded")
        try {
            val result = sdk().awaitPurchase(PurchaseParams.Builder(activity, pkg).build())
            if (result.customerInfo.entitlements[PREMIUM_ENTITLEMENT_ID]?.isActive == true) PurchaseOutcome.Purchased
            else PurchaseOutcome.Pending
        } catch (e: PurchasesTransactionException) {
            when {
                e.userCancelled -> PurchaseOutcome.Cancelled
                e.code == PurchasesErrorCode.PaymentPendingError -> PurchaseOutcome.Pending
                else -> throw e
            }
        }
    }

    override suspend fun restore(): Result<Boolean> = runCatching {
        sdk().awaitRestore().entitlements[PREMIUM_ENTITLEMENT_ID]?.isActive == true
    }

    private fun Package.toPlan() = PremiumPlan(
        basePlanId = when (packageType) {
            PackageType.MONTHLY -> "monthly"
            PackageType.ANNUAL -> "annual"
            // A Play product arrives as `premium:<base plan>`; anything else is its own id.
            else -> product.id.substringAfter(':', product.id)
        },
        formattedPrice = product.price.formatted,
        billingPeriod = product.period?.iso8601 ?: "",
        packageId = identifier
    )
}
