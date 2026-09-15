package com.example.freshtrack.data.billing

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.tasks.await

/**
 * What the server says about this account's premium. Mirrors the
 * `entitlements/{uid}` document the Cloud Function writes; the client can
 * read it and never write it.
 */
data class Entitlement(
    val isPremium: Boolean,
    /** Play's `subscriptionState`, for example `SUBSCRIPTION_STATE_CANCELED`. */
    val state: String,
    val basePlanId: String?,
    /** Epoch millis when the paid period ends, if Play said. */
    val expiryTime: Long?
) {
    val isCancelled: Boolean get() = state == "SUBSCRIPTION_STATE_CANCELED"
}

/**
 * The server side of a purchase. [verify] is the only way a purchase turns
 * into an entitlement: the token goes up, the server asks Play, and the
 * answer comes back. The app never decides for itself that it is premium.
 */
interface EntitlementRemote {
    suspend fun verify(purchaseToken: String): Result<Entitlement>

    /** The entitlement on record, or null if this account has never bought. */
    suspend fun current(uid: String): Result<Entitlement?>
}

class FirebaseEntitlementRemote(
    private val functions: FirebaseFunctions,
    private val firestore: FirebaseFirestore
) : EntitlementRemote {

    override suspend fun verify(purchaseToken: String): Result<Entitlement> = runCatching {
        val data = functions.getHttpsCallable("verifyPurchase")
            .call(mapOf("purchaseToken" to purchaseToken))
            .await()
            .data as? Map<*, *>
            ?: error("verifyPurchase returned no data")
        data.toEntitlement()
    }

    override suspend fun current(uid: String): Result<Entitlement?> = runCatching {
        firestore.collection("entitlements").document(uid).get().await()
            .data?.toEntitlement()
    }

    private fun Map<*, *>.toEntitlement() = Entitlement(
        isPremium = this["isPremium"] == true,
        state = this["state"] as? String ?: "",
        basePlanId = this["basePlanId"] as? String,
        expiryTime = (this["expiryTime"] as? Number)?.toLong()
    )
}
