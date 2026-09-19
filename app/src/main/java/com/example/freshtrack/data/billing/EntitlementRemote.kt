package com.example.freshtrack.data.billing

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/**
 * What the server says about this account's premium. Mirrors the
 * `entitlements/{uid}` document the webhook function writes from
 * RevenueCat's record; the client can read it and never write it.
 */
data class Entitlement(
    val isPremium: Boolean,
    /** `ACTIVE`, `CANCELLED`, `GRACE`, `PAUSED`, `REFUNDED`, `EXPIRED` or `NONE`. */
    val state: String,
    /** `monthly` or `annual` on Play; null for an App Store purchase. */
    val basePlanId: String?,
    /** Epoch millis when the paid period ends, if the store said. */
    val expiryTime: Long?
) {
    /** Still premium, but will not renew: Settings says "ends on" rather than "renews on". */
    val isCancelled: Boolean get() = state == "CANCELLED"
}

/**
 * The server's record of a purchase. The app never decides for itself that
 * it is premium: RevenueCat tells the server, the server writes this, and
 * the app reads it.
 */
interface EntitlementRemote {
    /** The entitlement on record, or null if this account has never bought. */
    suspend fun current(uid: String): Result<Entitlement?>
}

class FirebaseEntitlementRemote(
    private val firestore: FirebaseFirestore
) : EntitlementRemote {

    override suspend fun current(uid: String): Result<Entitlement?> = runCatching {
        firestore.collection("entitlements").document(uid).get().await()
            .data?.let { data ->
                Entitlement(
                    isPremium = data["isPremium"] == true,
                    state = data["state"] as? String ?: "",
                    basePlanId = data["basePlanId"] as? String,
                    expiryTime = (data["expiryTime"] as? Number)?.toLong()
                )
            }
    }
}
