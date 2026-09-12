package com.example.freshtrack.data.sync

/** Per-kitchen sync progress, in memory. */
class FakeSyncState : SyncState {
    private val bootstrapped = mutableSetOf<String>()
    private val uploaded = mutableMapOf<String, Int>()
    override fun isBootstrapped(kitchenId: String) = kitchenId in bootstrapped
    override fun markBootstrapped(kitchenId: String) { bootstrapped += kitchenId }
    override fun bootstrapEventsUploaded(kitchenId: String) = uploaded[kitchenId] ?: 0
    override fun setBootstrapEventsUploaded(kitchenId: String, count: Int) { uploaded[kitchenId] = count }
    private val itemsCursor = mutableMapOf<String, Long>()
    private val eventsCursor = mutableMapOf<String, Long>()
    override fun itemsCursor(kitchenId: String) = itemsCursor[kitchenId] ?: 0L
    override fun setItemsCursor(kitchenId: String, cursor: Long) { itemsCursor[kitchenId] = cursor }
    override fun eventsCursor(kitchenId: String) = eventsCursor[kitchenId] ?: 0L
    override fun setEventsCursor(kitchenId: String, cursor: Long) { eventsCursor[kitchenId] = cursor }
}

/** A scripted server: says whether the kitchen is premium, and fails the pushes it is told to. */
class FakeRemoteStore : RemoteStore {
    var premium = true
    var premiumError: RemoteError? = null
    var entitlementReads = 0
    val pushes = mutableListOf<RemoteWrite>()
    val failWith = mutableMapOf<String, RemoteError>()
    val existing = mutableSetOf<String>()

    override suspend fun ensureKitchenExists(kitchenId: String, ownerUid: String, name: String) =
        Result.success(Unit)

    override suspend fun isKitchenPremium(kitchenId: String): Result<Boolean> {
        entitlementReads++
        premiumError?.let { return Result.failure(it) }
        return Result.success(premium)
    }

    override suspend fun push(write: RemoteWrite): Result<Unit> {
        pushes += write
        failWith[write.operationId]?.let { return Result.failure(it) }
        existing += write.operationId
        return Result.success(Unit)
    }

    override suspend fun eventExists(kitchenId: String, operationId: String): Result<Boolean> =
        Result.success(operationId in existing)

    val bootstrappedItems = mutableMapOf<String, Map<String, Any?>>()
    val bootstrappedEvents = mutableListOf<String>()
    var onBootstrapItems: (suspend () -> Unit)? = null
    var failEventBatch: Int? = null
    val refuseEventBatchesContaining = mutableSetOf<String>()
    private var eventBatches = 0

    override suspend fun pushItems(kitchenId: String, items: List<Pair<String, Map<String, Any?>>>): Result<Unit> {
        items.forEach { (id, fields) -> bootstrappedItems[id] = fields }
        onBootstrapItems?.invoke()
        return Result.success(Unit)
    }

    override suspend fun pushEvents(kitchenId: String, events: List<Pair<String, Map<String, Any?>>>): Result<Unit> {
        eventBatches++
        if (events.any { it.first in refuseEventBatchesContaining }) return Result.failure(RemoteError.PermissionDenied())
        if (eventBatches == failEventBatch) return Result.failure(RemoteError.Transient())
        events.forEach { (id, _) -> bootstrappedEvents += id; existing += id }
        return Result.success(Unit)
    }

    /** What the server holds, for the pull side. Ordered by serverUpdatedAt. */
    val serverItems = mutableListOf<RemoteDocument>()
    val serverEvents = mutableListOf<RemoteDocument>()
    var fetchError: RemoteError? = null
    var fetches = 0

    override suspend fun fetchItemsSince(kitchenId: String, after: Long, limit: Int): Result<List<RemoteDocument>> =
        fetch(serverItems, after, limit)

    override suspend fun fetchEventsSince(kitchenId: String, after: Long, limit: Int): Result<List<RemoteDocument>> =
        fetch(serverEvents, after, limit)

    private fun fetch(from: List<RemoteDocument>, after: Long, limit: Int): Result<List<RemoteDocument>> {
        fetches++
        fetchError?.let { return Result.failure(it) }
        return Result.success(from.filter { it.serverUpdatedAt > after }.sortedBy { it.serverUpdatedAt }.take(limit))
    }

    override suspend fun deleteAccountData(kitchenId: String, uid: String) = Result.success(Unit)
}
