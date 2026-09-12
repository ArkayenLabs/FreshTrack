package com.example.freshtrack.data.sync

import com.example.freshtrack.data.local.TransactionRunner
import com.example.freshtrack.data.local.entities.ItemEntity
import com.example.freshtrack.data.local.entities.ItemEventEntity
import com.example.freshtrack.data.local.entities.ItemEventType
import com.example.freshtrack.data.repository.FakeItemDao
import com.example.freshtrack.data.repository.FakeItemEventDao
import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.DateSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The pull half of the transport, one property of `sync-design.md` §6 per
 * test. The invariant throughout: a pulled change never writes to the outbox
 * or appends an event on this device's behalf, and applying the same page
 * twice changes nothing the second time.
 */
class RemoteChangeApplierTest {

    private val kitchen = "personal-alice"
    private val itemDao = FakeItemDao()
    private val eventDao = FakeItemEventDao()
    private val remote = FakeRemoteStore()
    private val syncState = FakeSyncState()
    private val transactions = object : TransactionRunner {
        var runs = 0
        override suspend fun <T> run(block: suspend () -> T): T { runs++; return block() }
    }

    private fun applier(pageSize: Int = 200) =
        RemoteChangeApplier(itemDao, eventDao, remote, syncState, transactions, "device-a", pageSize)

    private fun row(id: String, name: String, revision: Long = 0L) = ItemEntity(
        id = id, kitchenId = kitchen, name = name, category = "Dairy & Eggs",
        expiryDate = LocalDate.of(2026, 9, 20), dateKind = DateKind.USE_BY,
        dateSource = DateSource.USER, dateConfidence = 1f, createdBy = "alice",
        lastEditedBy = "alice", revision = revision
    )

    /** A document as another device (or this one) would have written it. */
    private fun serverItem(id: String, name: String, at: Long, by: String = "device-b", op: String = "op-$at") =
        RemoteDocument(id, WireFormat.item(row(id, name), op, "alice", by), at)

    private fun serverEvent(op: String, itemId: String, at: Long) = RemoteDocument(
        op,
        WireFormat.event(
            ItemEventEntity(
                id = op, itemId = itemId, kitchenId = kitchen, type = ItemEventType.QUANTITY_USED,
                actorUid = "bob", quantity = 1, occurredAt = at, operationId = op
            )
        ),
        at
    )

    // ─── Another device's changes ───────────────────────────────────────────

    @Test
    fun `a change from another device is applied, with the server's revision`() = runTest {
        remote.serverItems += serverItem("milk", "Oat milk", at = 500)

        val outcome = applier().apply(kitchen)

        assertEquals(RemoteChangeApplier.Outcome.UpToDate(1, 0), outcome)
        val local = itemDao.rows.getValue("milk")
        assertEquals("Oat milk", local.name)
        assertEquals(500L, local.revision)
        assertEquals(kitchen, local.kitchenId)
    }

    @Test
    fun `a change older than what is already here is not applied`() = runTest {
        itemDao.insert(row("milk", "Newer local", revision = 900))
        remote.serverItems += serverItem("milk", "Older remote", at = 500)

        applier().apply(kitchen)

        assertEquals("Newer local", itemDao.rows.getValue("milk").name)
    }

    @Test
    fun `another device's events are appended, and nothing is queued for them`() = runTest {
        remote.serverEvents += serverEvent("op-b1", "milk", at = 500)

        val outcome = applier().apply(kitchen)

        assertEquals(RemoteChangeApplier.Outcome.UpToDate(0, 1), outcome)
        val event = eventDao.events.single()
        assertEquals("bob", event.actorUid)
        assertEquals(kitchen, event.kitchenId)
        assertEquals("op-b1", event.operationId)
    }

    // ─── This device's own writes, back from the server ────────────────────

    @Test
    fun `an own write coming back stamps the revision and touches nothing else`() = runTest {
        // Pushed "Milk", then edited locally to "Whole milk" before the push
        // came back. Applying the returned document would regress the edit.
        itemDao.insert(row("milk", "Whole milk"))
        remote.serverItems += serverItem("milk", "Milk", at = 500, by = "device-a")

        val outcome = applier().apply(kitchen)

        assertEquals(RemoteChangeApplier.Outcome.UpToDate(0, 0), outcome)
        val local = itemDao.rows.getValue("milk")
        assertEquals("Whole milk", local.name)
        assertEquals(500L, local.revision)
    }

    @Test
    fun `an own event coming back is not appended twice`() = runTest {
        eventDao.append(
            ItemEventEntity(
                id = "evt-1", itemId = "milk", kitchenId = kitchen, type = ItemEventType.QUANTITY_USED,
                actorUid = "alice", quantity = 1, occurredAt = 100, operationId = "op-a1"
            )
        )
        remote.serverEvents += serverEvent("op-a1", "milk", at = 500)

        val outcome = applier().apply(kitchen)

        assertEquals(RemoteChangeApplier.Outcome.UpToDate(0, 0), outcome)
        assertEquals(1, eventDao.events.size)
    }

    // ─── Cursor and paging ──────────────────────────────────────────────────

    @Test
    fun `the cursor moves to the last applied document, and a second run fetches nothing new`() = runTest {
        remote.serverItems += serverItem("milk", "Milk", at = 500)
        remote.serverEvents += serverEvent("op-b1", "milk", at = 700)

        applier().apply(kitchen)
        assertEquals(500L, syncState.itemsCursor(kitchen))
        assertEquals(700L, syncState.eventsCursor(kitchen))

        val again = applier().apply(kitchen)
        assertEquals(RemoteChangeApplier.Outcome.UpToDate(0, 0), again)
    }

    @Test
    fun `a backlog larger than a page is applied page by page`() = runTest {
        (1L..5L).forEach { remote.serverItems += serverItem("item-$it", "Item $it", at = it * 100) }

        val outcome = applier(pageSize = 2).apply(kitchen)

        assertEquals(RemoteChangeApplier.Outcome.UpToDate(5, 0), outcome)
        assertEquals(5, itemDao.rows.size)
        assertEquals(500L, syncState.itemsCursor(kitchen))
    }

    @Test
    fun `items and events keep separate cursors`() = runTest {
        // Two items, one event, page size one: after the first page the item
        // cursor is 100 and the event cursor must still be where it was, or
        // the event at 150 would never be seen.
        remote.serverItems += serverItem("a", "A", at = 100)
        remote.serverItems += serverItem("b", "B", at = 200)
        remote.serverEvents += serverEvent("op-e", "a", at = 150)

        val outcome = applier(pageSize = 1).apply(kitchen)

        assertEquals(RemoteChangeApplier.Outcome.UpToDate(2, 1), outcome)
    }

    @Test
    fun `a fetch that fails defers and leaves the cursor alone`() = runTest {
        remote.serverItems += serverItem("milk", "Milk", at = 500)
        remote.fetchError = RemoteError.Transient()

        val outcome = applier().apply(kitchen)

        assertTrue(outcome is RemoteChangeApplier.Outcome.Deferred)
        assertEquals(0L, syncState.itemsCursor(kitchen))
        assertTrue(itemDao.rows.isEmpty())
    }

    @Test
    fun `applying the same page twice changes nothing the second time`() = runTest {
        remote.serverItems += serverItem("milk", "Milk", at = 500)
        remote.serverEvents += serverEvent("op-b1", "milk", at = 500)

        applier().apply(kitchen)
        // A crash after the transaction but before the cursor write means the
        // page is delivered again.
        syncState.setItemsCursor(kitchen, 0)
        syncState.setEventsCursor(kitchen, 0)
        val again = applier().apply(kitchen)

        assertEquals(RemoteChangeApplier.Outcome.UpToDate(0, 0), again)
        assertEquals(1, itemDao.rows.size)
        assertEquals(1, eventDao.events.size)
    }

    @Test
    fun `each page is one transaction`() = runTest {
        (1L..3L).forEach { remote.serverItems += serverItem("item-$it", "Item $it", at = it * 100) }

        applier(pageSize = 2).apply(kitchen)

        // Two item pages, and events fetched once (empty, no transaction).
        assertEquals(2, transactions.runs)
    }
}
