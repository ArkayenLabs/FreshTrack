package com.example.freshtrack.data.sync

/**
 * What the app needs from the remote store, with no mention of the backend
 * providing it.
 *
 * Reduced to account lifecycle. The push and pull methods were shaped around
 * the previous product schema and have been removed along with it; the
 * replacement pushes queued outbox operations against the shared cross-platform
 * contract rather than mirroring rows.
 *
 * Erasure stays here regardless, because it is a Play requirement rather than a
 * sync feature: a user must be able to delete their cloud data whether or not
 * anything is currently syncing.
 */
interface RemoteProductStore {

    suspend fun ensurePantryExists(
        pantryId: String,
        ownerUid: String,
        name: String
    ): Result<Unit>

    /**
     * Erases everything stored for this user: every product document, the
     * pantry, and the user profile.
     *
     * Documents are deleted individually because Firestore does not cascade
     * into subcollections — removing the pantry alone would leave them orphaned
     * with no owner and no way to reach them.
     */
    suspend fun deleteAccountData(pantryId: String, uid: String): Result<Unit>
}
