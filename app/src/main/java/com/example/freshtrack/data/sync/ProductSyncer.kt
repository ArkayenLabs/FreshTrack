package com.example.freshtrack.data.sync

import com.example.freshtrack.data.local.dao.ProductDao
import com.example.freshtrack.data.preferences.SyncPreferences
import com.example.freshtrack.data.session.UserSession

/**
 * Outcome of a sync attempt. Distinguishing these matters because the retry
 * policy differs: a network failure is worth retrying, a permission denial is
 * not — it will keep failing until the user's plan changes.
 */
sealed interface SyncResult {
    /** Not signed in. Nothing to do; a guest's data never leaves the device. */
    data object SkippedSignedOut : SyncResult

    /** Signed in but not premium. Expected, not an error. Do not retry. */
    data object SkippedNotPremium : SyncResult

    data class Success(val pulled: Int, val pushed: Int) : SyncResult

    /** Transient — offline, timeout, quota. Worth retrying with backoff. */
    data class Retryable(val cause: Throwable) : SyncResult

    /** Permanent for now; retrying will not help. */
    data class Failed(val cause: Throwable) : SyncResult
}

/**
 * Pushes local changes up and pulls remote changes down for the active pantry.
 *
 * Room stays the source of truth: this never blocks a read, and a total sync
 * failure leaves the app fully usable offline.
 */
class ProductSyncer(
    private val productDao: ProductDao,
    private val remote: RemoteProductStore,
    private val session: UserSession,
    private val syncPrefs: SyncPreferences
) {

    suspend fun sync(): SyncResult {
        if (!session.isSignedIn()) return SyncResult.SkippedSignedOut

        val pantryId = session.activePantryId()
        val uid = session.currentUserId()

        remote.ensurePantryExists(pantryId, uid, DEFAULT_PANTRY_NAME)
            .onFailure { return it.toSyncResult() }

        // ─── Pull ────────────────────────────────────────────────────────────
        val lastPulled = syncPrefs.lastPulledAt(pantryId)
        val remoteChanges = remote.fetchChangedSince(pantryId, lastPulled)
            .getOrElse { return it.toSyncResult() }

        val pulledIds = mutableSetOf<String>()
        var newestPulled = lastPulled

        if (remoteChanges.isNotEmpty()) {
            val toApply = remoteChanges.mapNotNull { incoming ->
                val local = productDao.getByIdIncludingDeleted(incoming.id)
                // Last write wins. A local row that is newer is left alone and
                // will be pushed on the next step instead.
                if (local != null && local.updatedAt >= incoming.updatedAt) {
                    null
                } else {
                    // The remote document carries no image path, so keep
                    // whatever this device already had.
                    incoming.copy(imageUri = local?.imageUri)
                }
            }
            if (toApply.isNotEmpty()) {
                productDao.upsertFromRemote(toApply)
                toApply.forEach { pulledIds += it.id }
            }
            newestPulled = remoteChanges.maxOf { it.updatedAt }
        }

        // ─── Push ────────────────────────────────────────────────────────────
        val lastPushed = syncPrefs.lastPushedAt(pantryId)
        val localChanges = productDao.getChangedSince(pantryId, lastPushed)
            // Anything just applied from the server would otherwise be echoed
            // straight back with identical content.
            .filterNot { it.id in pulledIds }

        if (localChanges.isNotEmpty()) {
            remote.push(pantryId, localChanges)
                .onFailure { return it.toSyncResult() }
            syncPrefs.setLastPushedAt(pantryId, localChanges.maxOf { it.updatedAt })
        }

        // Only advanced once the pull actually applied, so a crash mid-sync
        // replays rather than skips.
        syncPrefs.setLastPulledAt(pantryId, newestPulled)

        return SyncResult.Success(pulled = pulledIds.size, pushed = localChanges.size)
    }

    /** Clears watermarks so the next account does not inherit this one's position. */
    fun onSignedOut() = syncPrefs.clear()

    private fun Throwable.toSyncResult(): SyncResult = when (this) {
        // Expected for a free account: rules allow reads but refuse cloud
        // writes. Not an error, and not worth retrying.
        is RemoteError.PermissionDenied -> SyncResult.SkippedNotPremium
        is RemoteError.Transient -> SyncResult.Retryable(this)
        else -> SyncResult.Failed(this)
    }

    private fun Result<*>.toSyncResult(): SyncResult =
        exceptionOrNull()?.toSyncResult() ?: SyncResult.Failed(IllegalStateException("unknown"))

    companion object {
        private const val DEFAULT_PANTRY_NAME = "My Pantry"
    }
}
