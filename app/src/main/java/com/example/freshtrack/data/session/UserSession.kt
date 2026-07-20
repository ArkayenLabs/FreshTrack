package com.example.freshtrack.data.session

import com.example.freshtrack.data.local.entities.GUEST_USER_ID
import com.google.firebase.auth.FirebaseAuth

/**
 * Single source of truth for "whose data is this".
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
}
