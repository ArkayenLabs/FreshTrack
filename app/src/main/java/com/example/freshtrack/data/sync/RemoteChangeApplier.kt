package com.example.freshtrack.data.sync

import com.example.freshtrack.data.local.TransactionRunner
import com.example.freshtrack.data.local.dao.ItemDao
import com.example.freshtrack.data.local.dao.ItemEventDao

/**
 * Brings one kitchen up to date with the server, by cursor.
 *
 * The pull half of `sync-design.md` §6. Items and events are paged separately
 * from their own cursors, oldest first; each page is applied in one Room
 * transaction and the cursor is moved only after that commits, so a crash
 * mid-page re-delivers rather than skips. Everything applied is idempotent —
 * a row is only replaced by a strictly newer one, and a replayed event inserts
 * nothing — so re-delivery is safe.
 *
 * Nothing here writes to the outbox or the ledger on this device's behalf. A
 * pulled change already happened somewhere; recording it again would push it
 * straight back and count it twice.
 */
class RemoteChangeApplier(
    private val itemDao: ItemDao,
    private val eventDao: ItemEventDao,
    private val remote: RemoteStore,
    private val syncState: SyncState,
    private val transactions: TransactionRunner,
    private val clientId: String,
    private val pageSize: Int = 200
) {

    sealed interface Outcome {
        data class UpToDate(val itemsApplied: Int, val eventsApplied: Int) : Outcome
        data class Deferred(val error: RemoteError) : Outcome
    }

    suspend fun apply(kitchenId: String): Outcome {
        var items = 0
        var events = 0

        // Items first, then events. An event that arrives before its item
        // cannot mislead anything, because events are never used to decide
        // whether an item change is this device's own.
        while (true) {
            val page = remote.fetchItemsSince(kitchenId, syncState.itemsCursor(kitchenId), pageSize)
                .getOrElse { return Outcome.Deferred(it.asRemoteError()) }
            if (page.isEmpty()) break
            items += applyItems(kitchenId, page)
            syncState.setItemsCursor(kitchenId, page.last().serverUpdatedAt)
            if (page.size < pageSize) break
        }

        while (true) {
            val page = remote.fetchEventsSince(kitchenId, syncState.eventsCursor(kitchenId), pageSize)
                .getOrElse { return Outcome.Deferred(it.asRemoteError()) }
            if (page.isEmpty()) break
            events += applyEvents(kitchenId, page)
            syncState.setEventsCursor(kitchenId, page.last().serverUpdatedAt)
            if (page.size < pageSize) break
        }

        return Outcome.UpToDate(items, events)
    }

    private suspend fun applyItems(kitchenId: String, page: List<RemoteDocument>): Int =
        transactions.run {
            var applied = 0
            for (doc in page) {
                if (doc.fields["lastClientId"] == clientId) {
                    // Our own write, back from the server. The row already says
                    // this — or something newer, still queued — so only the
                    // server's ordering value is new information.
                    itemDao.setRevision(doc.id, doc.serverUpdatedAt)
                    continue
                }
                val local = itemDao.getByIdIncludingDeleted(doc.id)
                if (local != null && local.revision >= doc.serverUpdatedAt) continue

                itemDao.upsertFromRemote(
                    listOf(WireFormat.itemFrom(doc.fields, doc.id, kitchenId, doc.serverUpdatedAt))
                )
                applied++
            }
            applied
        }

    private suspend fun applyEvents(kitchenId: String, page: List<RemoteDocument>): Int =
        transactions.run {
            // IGNORE on the unique operationId index: our own events, and any
            // delivered twice, insert nothing. The return value says which did.
            val rows = page.map { WireFormat.eventFrom(it.fields, it.id, kitchenId) }
            eventDao.appendAll(rows).count { it != -1L }
        }

    private fun Throwable.asRemoteError(): RemoteError =
        this as? RemoteError ?: RemoteError.Permanent(this)
}
