package com.example.freshtrack.data.sync

/**
 * What the sync engine needs from the backend, with no mention of the backend
 * providing it.
 *
 * Shaped around the remote model in `sync-design.md` §3: a kitchen document,
 * its items, and its append-only events. Every call returns a [Result] rather
 * than throwing, and the failure is always a [RemoteError], so the engine can
 * decide what to do next without importing a Firebase type.
 *
 * The push and pull methods are added as the engine that uses them is built;
 * an interface method with no caller is a promise nobody has tested.
 */
interface RemoteStore {

    /**
     * Creates the kitchen document if it is not already there. Kitchen ids are
     * derived, so calling this repeatedly is harmless.
     */
    suspend fun ensureKitchenExists(
        kitchenId: String,
        ownerUid: String,
        name: String
    ): Result<Unit>

    /**
     * Whether the server says this kitchen may write to the cloud.
     *
     * Read from the kitchen document, which only a trusted server may flag.
     * The engine treats `false` as "do not push", not as "push and fail".
     */
    suspend fun isKitchenPremium(kitchenId: String): Result<Boolean>

    /**
     * Writes one item state and appends its event as a single atomic batch, so
     * the row and the ledger cannot diverge on the server.
     *
     * The event document id is the operation id. A retry of an operation the
     * server already has is refused by the rules; the caller distinguishes
     * that from an entitlement refusal with [eventExists].
     */
    suspend fun push(write: RemoteWrite): Result<Unit>

    /** Whether the event for this operation is already on the server. */
    suspend fun eventExists(kitchenId: String, operationId: String): Result<Boolean>

    /**
     * A first backup's rows. Setting an item is idempotent, so the store may
     * split these into as many batches as it needs and the caller may repeat
     * the whole call after an interruption.
     */
    suspend fun pushItems(kitchenId: String, items: List<Pair<String, Map<String, Any?>>>): Result<Unit>

    /**
     * A first backup's ledger, one atomic batch, so the caller can record
     * exactly how far it got. At most [MAX_BATCH] entries per call: an event
     * cannot be written twice, so the caller resumes rather than repeats.
     */
    suspend fun pushEvents(kitchenId: String, events: List<Pair<String, Map<String, Any?>>>): Result<Unit>

    /**
     * Items changed on the server after [after], oldest first, at most [limit].
     *
     * [after] and each result's [RemoteDocument.serverUpdatedAt] are the same
     * unit — microseconds, see [RemoteDocument] — so that a cursor set from a
     * result excludes exactly that result next time. A lossy conversion here
     * would re-deliver the same document on every run.
     */
    suspend fun fetchItemsSince(kitchenId: String, after: Long, limit: Int): Result<List<RemoteDocument>>

    /** Events appended on the server after [after], oldest first, at most [limit]. */
    suspend fun fetchEventsSince(kitchenId: String, after: Long, limit: Int): Result<List<RemoteDocument>>

    /**
     * Erases everything stored for this user: every item, every event, the
     * kitchen, and the user profile.
     *
     * Documents are deleted individually because Firestore does not cascade
     * into subcollections — removing the kitchen alone would leave them
     * orphaned with no owner and no way to reach them.
     */
    suspend fun deleteAccountData(kitchenId: String, uid: String): Result<Unit>

    companion object {
        /** Firestore's limit on writes in one batch. */
        const val MAX_BATCH = 500
    }
}

/**
 * One document as read from the server.
 *
 * [serverUpdatedAt] is the server's clock in microseconds since the epoch —
 * a Firestore timestamp is seconds plus nanoseconds, and microseconds round
 * trip through a Long exactly where milliseconds would not. It is the pull
 * cursor and, once applied, the row's `revision`.
 */
data class RemoteDocument(
    val id: String,
    val fields: Map<String, Any?>,
    val serverUpdatedAt: Long
)

/**
 * One operation, ready for the wire.
 *
 * Field maps rather than typed documents so the store stays a thin adapter;
 * what goes in them is decided by [WireFormat], which is pure and tested.
 * The store adds the server timestamp itself, because that is a fact about
 * the transport and not about the item.
 */
data class RemoteWrite(
    val kitchenId: String,
    val itemId: String,
    val itemFields: Map<String, Any?>,
    val operationId: String,
    val eventFields: Map<String, Any?>
)
