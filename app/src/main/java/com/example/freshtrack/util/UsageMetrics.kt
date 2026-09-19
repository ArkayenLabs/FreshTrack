package com.example.freshtrack.util

import android.os.Bundle

/**
 * The eight questions that can change a build decision, and nothing else.
 *
 * Fired from the repository's write path and from the two Premium screens,
 * so every entry point is counted once and none is missed. No item names,
 * barcodes, prices or dates ever go through here: only that something
 * happened, how, and how big the kitchen was at the time. Collection is
 * off unless the person opted in (`ConsentPreferences`), and
 * `AnalyticsHelper.applyConsent` is what turns it on.
 *
 * The questions, and the events that answer them:
 *  1. Do people get a first item in?            `first_item`
 *  2. Do they reach a real kitchen?             `third_item`
 *  3. Which way in works?                       `add_completed{method}`
 *  4. Do items get resolved at all?             `item_resolved{outcome, overdue}` (+ `resolution_undone`)
 *  5. Used vs binned, among resolved            same event
 *  6. Do people who reach 3 items come back?    Firebase retention on `third_item`
 *  7. Premium intent → paywall → purchase       `premium_intent{source}`, `paywall_shown`, `purchase_confirmed{plan}`
 *  8. Household invite → join                   not yet; the household is not built
 */
interface UsageMetrics {
    fun itemAdded(method: AddMethod, activeCountAfter: Int)
    fun itemResolved(outcome: Outcome, wasOverdue: Boolean)
    fun resolutionUndone()
    fun premiumIntent(source: String)
    fun paywallShown()
    fun purchaseConfirmed(plan: String?)
    fun reviewPrompted()

    enum class AddMethod { BARCODE, RECEIPT, MANUAL }
    enum class Outcome { USED, BINNED }

    /** For tests and for anything that must not touch Firebase. */
    object NoOp : UsageMetrics {
        override fun itemAdded(method: AddMethod, activeCountAfter: Int) = Unit
        override fun itemResolved(outcome: Outcome, wasOverdue: Boolean) = Unit
        override fun resolutionUndone() = Unit
        override fun premiumIntent(source: String) = Unit
        override fun paywallShown() = Unit
        override fun purchaseConfirmed(plan: String?) = Unit
        override fun reviewPrompted() = Unit
    }
}

/** The real one, over Firebase Analytics through [AnalyticsHelper]. */
class FirebaseUsageMetrics : UsageMetrics {
    override fun itemAdded(method: UsageMetrics.AddMethod, activeCountAfter: Int) {
        AnalyticsHelper.log("add_completed", Bundle().apply { putString("method", method.name.lowercase()) })
        // Milestones, not counters: the first item proves the way in works;
        // the third is when Today has something to say.
        when (activeCountAfter) {
            1 -> AnalyticsHelper.log("first_item")
            3 -> AnalyticsHelper.log("third_item")
        }
    }

    override fun itemResolved(outcome: UsageMetrics.Outcome, wasOverdue: Boolean) {
        AnalyticsHelper.log("item_resolved", Bundle().apply {
            putString("outcome", outcome.name.lowercase())
            putBoolean("overdue", wasOverdue)
        })
    }

    override fun resolutionUndone() = AnalyticsHelper.log("resolution_undone")

    override fun premiumIntent(source: String) =
        AnalyticsHelper.log("premium_intent", Bundle().apply { putString("source", source) })

    override fun paywallShown() = AnalyticsHelper.log("paywall_shown")

    override fun purchaseConfirmed(plan: String?) =
        AnalyticsHelper.log("purchase_confirmed", Bundle().apply { putString("plan", plan ?: "unknown") })

    override fun reviewPrompted() = AnalyticsHelper.log("review_prompted")
}
