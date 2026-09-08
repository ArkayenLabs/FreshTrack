package com.example.freshtrack.data.session

import com.example.freshtrack.data.local.entities.GUEST_USER_ID
import com.example.freshtrack.data.local.entities.LOCAL_KITCHEN_ID
import com.example.freshtrack.data.local.entities.LOCAL_PANTRY_ID
import com.example.freshtrack.data.local.entities.personalKitchenId
import com.example.freshtrack.data.local.entities.personalPantryId
import com.google.firebase.auth.FirebaseAuth

/**
 * Whose data this is, and which kitchen is being viewed.
 *
 * An interface so that logic depending on it can be tested on the JVM without
 * the Firebase runtime — the answer to "which kitchen" is what decides what
 * every query returns, so it needs to be trivially substitutable in tests.
 */
interface KitchenSession {
    fun currentUserId(): String
    fun isSignedIn(): Boolean

    /**
     * The kitchen being viewed, and the key every user-facing query filters on.
     *
     * Signed out this is the local kitchen; signed in it is the user's personal
     * one. When household sharing lands this becomes a selected value rather
     * than a derived one, which is why it is a single accessor.
     */
    fun activeKitchenId(): String
}

/**
 * Single source of truth for "whose data is this, and which pantry am I in".
 *
 * Resolved on every call rather than cached, so a sign-in or sign-out takes
 * effect immediately without anything having to invalidate a stored value.
 */
class UserSession(
    private val auth: FirebaseAuth
) : KitchenSession {
    /** Firebase uid when signed in, otherwise the guest sentinel. */
    override fun currentUserId(): String = auth.currentUser?.uid ?: GUEST_USER_ID

    override fun isSignedIn(): Boolean = auth.currentUser != null

    override fun activeKitchenId(): String {
        val uid = auth.currentUser?.uid ?: return LOCAL_KITCHEN_ID
        return personalKitchenId(uid)
    }

    /**
     * The pantry currently being viewed, and the key every user-facing query
     * filters on.
     *
     * Signed out this is the local pantry; signed in it is the user's personal
     * pantry. Once household sharing exists this becomes a selectable value
     * rather than a derived one — hence the single accessor.
     */
    fun activePantryId(): String {
        val uid = auth.currentUser?.uid ?: return LOCAL_PANTRY_ID
        return personalPantryId(uid)
    }
}
