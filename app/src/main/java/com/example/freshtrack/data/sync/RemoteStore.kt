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
     * Erases everything stored for this user: every item, every event, the
     * kitchen, and the user profile.
     *
     * Documents are deleted individually because Firestore does not cascade
     * into subcollections — removing the kitchen alone would leave them
     * orphaned with no owner and no way to reach them.
     */
    suspend fun deleteAccountData(kitchenId: String, uid: String): Result<Unit>
}

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
