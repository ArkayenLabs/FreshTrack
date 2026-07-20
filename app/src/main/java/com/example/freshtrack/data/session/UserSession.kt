package com.example.freshtrack.data.session

import com.example.freshtrack.data.local.entities.GUEST_USER_ID
import com.example.freshtrack.data.local.entities.LOCAL_PANTRY_ID
import com.example.freshtrack.data.local.entities.personalPantryId
import com.google.firebase.auth.FirebaseAuth

/**
 * Single source of truth for "whose data is this, and which pantry am I in".
 *
 * Resolved on every call rather than cached, so a sign-in or sign-out takes
 * effect immediately without anything having to invalidate a stored value.
 */
class UserSession(
    private val auth: FirebaseAuth
) {
    /** Firebase uid when signed in, otherwise the guest sentinel. */
    fun currentUserId(): String = auth.currentUser?.uid ?: GUEST_USER_ID

    fun isSignedIn(): Boolean = auth.currentUser != null

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
