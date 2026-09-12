package com.example.freshtrack.data.sync

import com.example.freshtrack.data.session.KitchenSession
import com.example.freshtrack.util.AppClock

/**
 * One sync run for the active kitchen: make sure the kitchen document exists,
 * push what is queued, pull what is new, and record how it went.
 *
 * Kept out of the worker so it can be tested on the JVM. The worker only maps
 * [Result] onto WorkManager's idea of retry.
 *
 * Signed out, it does nothing at all — not even a read. A guest's data never
 * leaves the device.
 */
class SyncRun(
    private val session: KitchenSession,
    private val remote: RemoteStore,
    private val pusher: OutboxPusher,
    private val applier: RemoteChangeApplier,
    private val syncState: SyncState,
    private val clock: AppClock
) {

    enum class Result {
        /** Not signed in; nothing to do and nothing attempted. */
        SIGNED_OUT,

        /** Everything queued went up and everything new came down. */
        SYNCED,

        /** The kitchen is not premium. Expected; the queue waits. */
        NOT_ENTITLED,

        /** Something temporary stopped the run. Worth retrying with backoff. */
        DEFERRED
    }

    suspend fun run(): Result {
        if (!session.isSignedIn()) return Result.SIGNED_OUT

        val kitchenId = session.activeKitchenId()
        val uid = session.currentUserId()

        // The rules look the kitchen document up on every item and event write,
        // so it has to be there before anything else is tried. Creating it is
        // free for any signed-in user; only writing into it is premium.
        remote.ensureKitchenExists(kitchenId, uid, DEFAULT_KITCHEN_NAME)
            .onFailure { return record(if (it is RemoteError.PermissionDenied) Result.NOT_ENTITLED else Result.DEFERRED) }

        val pushed = when (pusher.push(kitchenId)) {
            is OutboxPusher.Outcome.Drained -> true
            is OutboxPusher.Outcome.NotEntitled -> return record(Result.NOT_ENTITLED)
            is OutboxPusher.Outcome.Deferred -> false
        }

        // Pull even when the push was deferred: what the other device did is
        // worth having regardless of whether ours got through.
        val pulled = when (applier.apply(kitchenId)) {
            is RemoteChangeApplier.Outcome.UpToDate -> true
            is RemoteChangeApplier.Outcome.Deferred -> false
        }

        return record(if (pushed && pulled) Result.SYNCED else Result.DEFERRED)
    }

    private fun record(result: Result): Result {
        syncState.setLastRun(result)
        if (result == Result.SYNCED) syncState.setLastSuccessAt(clock.nowMillis())
        return result
    }

    private companion object {
        /** The personal kitchen's name on the server; not shown anywhere yet. */
        const val DEFAULT_KITCHEN_NAME = "My kitchen"
    }
}
