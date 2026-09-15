package com.example.freshtrack.data.billing

import com.example.freshtrack.data.session.KitchenSession

class NotSignedInException : Exception("Premium needs an account")

/**
 * Turns a Play purchase into a server-side entitlement.
 *
 * Three ways in, one path through: a premium purchase token goes to
 * [EntitlementRemote.verify], and when the server says premium, [onEntitled]
 * fires so the backup that was waiting on it starts at once. Nothing is
 * cached here — the kitchen document is the truth, and `SyncRun` reads it.
 *
 * Signed out, every call fails without touching Play or the network: the
 * server binds a token to a uid, and a guest has none.
 */
class PremiumClaim(
    private val billing: PlayBilling,
    private val remote: EntitlementRemote,
    private val session: KitchenSession,
    private val onEntitled: () -> Unit
) {

    /** Straight after the purchase sheet: the purchases Play just reported. */
    suspend fun claim(purchases: List<OwnedPurchase>): Result<Entitlement> {
        if (!session.isSignedIn()) return Result.failure(NotSignedInException())
        val premium = purchases.firstOrNull { it.isPremium }
            ?: return Result.failure(IllegalStateException("No premium purchase to claim"))
        return present(premium)
    }

    /**
     * On resume. Only what Play is still waiting on — a purchase the app
     * did not get to verify, because it died between the sheet closing and
     * the call going up. In the common case this is one Play query and no
     * network at all.
     */
    suspend fun claimUnacknowledged(): Result<Entitlement?> {
        if (!session.isSignedIn()) return Result.success(null)
        return billing.ownedPurchases().map { owned ->
            owned.filter { it.isPremium && !it.isAcknowledged }
        }.mapCatching { pending ->
            pending.firstOrNull()?.let { present(it).getOrThrow() }
        }
    }

    /**
     * "Restore purchases": everything Play holds, acknowledged or not,
     * presented again. Covers a reinstall on a new account and a purchase
     * Play reports as already owned.
     */
    suspend fun restore(): Result<Entitlement?> {
        if (!session.isSignedIn()) return Result.failure(NotSignedInException())
        return billing.ownedPurchases().mapCatching { owned ->
            owned.filter { it.isPremium }
                .map { present(it).getOrThrow() }
                .let { results -> results.firstOrNull { it.isPremium } ?: results.firstOrNull() }
        }
    }

    private suspend fun present(purchase: OwnedPurchase): Result<Entitlement> =
        remote.verify(purchase.purchaseToken).onSuccess { if (it.isPremium) onEntitled() }
}
