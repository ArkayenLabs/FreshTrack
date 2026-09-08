package com.example.freshtrack.data.repository

import com.example.freshtrack.data.local.TransactionRunner
import com.example.freshtrack.data.local.entities.ItemEventType
import com.example.freshtrack.data.local.entities.ItemState
import com.example.freshtrack.data.local.entities.LOCAL_KITCHEN_ID
import com.example.freshtrack.data.local.entities.OutboxOperationType
import com.example.freshtrack.data.session.KitchenSession
import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.DateSource
import com.example.freshtrack.domain.model.ExpiryDate
import com.example.freshtrack.domain.model.Item
import com.example.freshtrack.util.AppClock
import com.example.freshtrack.util.IdGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * These are about the write path, not about SQLite. What matters is that every
 * mutation lands in three places together — the row, the ledger and the outbox
 * — and that the ledger records the right amount.
 */
class ItemRepositoryImplTest {

    private lateinit var itemDao: FakeItemDao
    private lateinit var eventDao: FakeItemEventDao
    private lateinit var outboxDao: FakeOutboxDao
    private lateinit var repository: ItemRepositoryImpl

    private val today = LocalDate.of(2026, 9, 7)
    private var now = 1_000_000L
    private var idCounter = 0

    private val clock = object : AppClock {
        override fun nowMillis() = now
        override fun today() = today
    }

    private val ids = object : IdGenerator {
        override fun newId() = "id-${++idCounter}"
    }

    private val session = object : KitchenSession {
        var signedIn = false
        var uid = "guest"
        override fun currentUserId() = uid
        override fun isSignedIn() = signedIn
        override fun activeKitchenId() =
            if (signedIn) "personal-$uid" else LOCAL_KITCHEN_ID
    }

    private val transactions = object : TransactionRunner {
        var depth = 0
        var maxDepth = 0
        override suspend fun <T> run(block: suspend () -> T): T {
            depth++
            maxDepth = maxOf(maxDepth, depth)
            try {
                return block()
            } finally {
                depth--
            }
        }
    }

    @Before
    fun setUp() {
        itemDao = FakeItemDao()
        eventDao = FakeItemEventDao()
        outboxDao = FakeOutboxDao()
        repository = ItemRepositoryImpl(
            itemDao = itemDao,
            eventDao = eventDao,
            outboxDao = outboxDao,
            session = session,
            transactions = transactions,
            clock = clock,
            ids = ids,
            clientId = "test-client",
            serialise = { it.id }
        )
    }

    private fun sampleItem(
        name: String = "Milk",
        quantity: Int = 1,
        expiry: LocalDate = LocalDate.of(2026, 9, 10)
    ) = Item(
        id = "ignored-the-repository-mints-its-own",
        name = name,
        category = "Dairy",
        expiry = ExpiryDate.enteredByUser(expiry, DateKind.USE_BY, atMillis = now),
        quantity = quantity
    )

    // ─── The three writes land together ──────────────────────────────────────

    @Test
    fun `adding an item writes the row, an event and an outbox entry`() = runTest {
        val id = repository.add(sampleItem())

        assertNotNull(itemDao.rows[id])
        assertEquals(1, eventDao.events.size)
        assertEquals(ItemEventType.ITEM_CREATED, eventDao.events.single().type)
        assertEquals(1, outboxDao.operations.size)
        assertEquals(OutboxOperationType.CREATE, outboxDao.operations.single().operationType)
    }

    @Test
    fun `all three writes happen inside one transaction`() = runTest {
        repository.add(sampleItem())

        assertEquals(1, transactions.maxDepth)
    }

    @Test
    fun `the event and the outbox entry share an operation id`() = runTest {
        repository.add(sampleItem())

        assertEquals(
            eventDao.events.single().operationId,
            outboxDao.operations.single().operationId
        )
    }

    @Test
    fun `ownership is stamped by the repository, not taken from the caller`() = runTest {
        session.signedIn = true
        session.uid = "alice"

        val id = repository.add(sampleItem())

        val row = itemDao.rows.getValue(id)
        assertEquals("personal-alice", row.kitchenId)
        assertEquals("alice", row.createdBy)
    }

    // ─── Partial use ─────────────────────────────────────────────────────────

    @Test
    fun `using part of a batch decrements it and leaves it active`() = runTest {
        val id = repository.add(sampleItem(quantity = 3))
        eventDao.events.clear()

        repository.use(id, amount = 1)

        val row = itemDao.rows.getValue(id)
        assertEquals(2, row.quantity)
        assertEquals(ItemState.ACTIVE, row.state)
        assertNull(row.resolvedAt)
    }

    @Test
    fun `a partial use records the amount used, not one item`() = runTest {
        val id = repository.add(sampleItem(quantity = 3))
        eventDao.events.clear()

        repository.use(id, amount = 2)

        val event = eventDao.events.single()
        assertEquals(ItemEventType.QUANTITY_USED, event.type)
        assertEquals(2, event.quantity)
    }

    @Test
    fun `a partial use creates no second item row`() = runTest {
        val id = repository.add(sampleItem(quantity = 3))

        repository.use(id, amount = 1)

        // The previous design split a partial use into a synthetic resolved row,
        // which then showed up in history as an item the user never had.
        assertEquals(1, itemDao.rows.size)
    }

    @Test
    fun `using the whole batch resolves the item`() = runTest {
        val id = repository.add(sampleItem(quantity = 2))

        repository.use(id, amount = 2)

        val row = itemDao.rows.getValue(id)
        assertEquals(ItemState.USED, row.state)
        assertEquals(now, row.resolvedAt)
    }

    @Test
    fun `using more than remains is capped and still resolves the item`() = runTest {
        val id = repository.add(sampleItem(quantity = 2))
        eventDao.events.clear()

        repository.use(id, amount = 99)

        assertEquals(ItemState.USED, itemDao.rows.getValue(id).state)
        assertEquals(2, eventDao.events.single().quantity)
    }

    @Test
    fun `discarding records a discard, not a use`() = runTest {
        val id = repository.add(sampleItem(quantity = 1))
        eventDao.events.clear()

        repository.discard(id)

        assertEquals(ItemEventType.QUANTITY_DISCARDED, eventDao.events.single().type)
        assertEquals(ItemState.DISCARDED, itemDao.rows.getValue(id).state)
    }

    // ─── Provenance is respected ─────────────────────────────────────────────

    @Test
    fun `freezing sets a user confirmed frozen-until date`() = runTest {
        val id = repository.add(sampleItem())
        eventDao.events.clear()

        repository.freeze(id, LocalDate.of(2026, 12, 1), freezerLocationId = "loc-freezer")

        val row = itemDao.rows.getValue(id)
        assertEquals(LocalDate.of(2026, 12, 1), row.expiryDate)
        assertEquals(DateKind.FROZEN_UNTIL, row.dateKind)
        assertEquals(DateSource.USER, row.dateSource)
        assertEquals(now, row.dateConfirmedByUserAt)
        assertEquals("loc-freezer", row.locationId)
        assertEquals(ItemEventType.ITEM_FROZEN, eventDao.events.single().type)
    }

    @Test
    fun `confirming a date pins it without changing what it says`() = runTest {
        val recognised = sampleItem().copy(
            expiry = ExpiryDate.recognised(
                LocalDate.of(2026, 9, 12), DateKind.BEST_BEFORE, DateSource.PRINTED_OCR, 0.7f
            )
        )
        val id = repository.add(recognised)
        eventDao.events.clear()

        repository.confirmDate(id)

        val row = itemDao.rows.getValue(id)
        assertEquals(LocalDate.of(2026, 9, 12), row.expiryDate)
        assertEquals(DateKind.BEST_BEFORE, row.dateKind)
        assertEquals(now, row.dateConfirmedByUserAt)
        assertEquals(ItemEventType.DATE_CONFIRMED, eventDao.events.single().type)
    }

    // ─── Deletion keeps history ──────────────────────────────────────────────

    @Test
    fun `deleting is a tombstone and queues one`() = runTest {
        val id = repository.add(sampleItem())
        outboxDao.operations.clear()

        repository.delete(id)

        assertTrue(itemDao.rows.getValue(id).isDeleted)
        assertEquals(
            OutboxOperationType.TOMBSTONE,
            outboxDao.operations.single().operationType
        )
    }

    @Test
    fun `clearing history does not erase the ledger`() = runTest {
        val id = repository.add(sampleItem())
        repository.discard(id)
        val eventsBefore = eventDao.events.size

        repository.clearHistory()

        // Tidying the list must not rewrite what the user was told they did.
        assertEquals(eventsBefore, eventDao.events.size)
    }

    // ─── Idempotency ─────────────────────────────────────────────────────────

    @Test
    fun `replaying an operation id records the event once`() = runTest {
        val id = repository.add(sampleItem(quantity = 5))
        val appended = eventDao.events.single()

        // Simulate a retried sync applying the same operation again.
        eventDao.append(appended)
        eventDao.append(appended)

        assertEquals(1, eventDao.events.count { it.operationId == appended.operationId })
    }

    // ─── Import ──────────────────────────────────────────────────────────────

    @Test
    fun `import skips duplicates within the file itself`() = runTest {
        val summary = repository.import(
            listOf(
                sampleItem(name = "Milk"),
                sampleItem(name = "milk"),
                sampleItem(name = "Bread")
            )
        )

        assertEquals(2, summary.imported)
        assertEquals(1, summary.skippedDuplicates)
    }

    @Test
    fun `import ignores rows with no name`() = runTest {
        val summary = repository.import(listOf(sampleItem(name = "   ")))

        assertEquals(0, summary.imported)
        assertEquals(0, summary.skippedDuplicates)
    }

    // ─── Claiming ────────────────────────────────────────────────────────────

    @Test
    fun `claiming does nothing while signed out`() = runTest {
        repository.add(sampleItem())

        assertEquals(0, repository.claimLocalData())
    }

    @Test
    fun `signing in adopts local items, events and queued operations`() = runTest {
        repository.add(sampleItem())
        session.signedIn = true
        session.uid = "alice"

        val claimed = repository.claimLocalData()

        assertEquals(1, claimed)
        assertTrue(itemDao.rows.values.all { it.kitchenId == "personal-alice" })
        assertTrue(eventDao.events.all { it.kitchenId == "personal-alice" })
        assertTrue(outboxDao.operations.all { it.kitchenId == "personal-alice" })
    }

    // ─── Undo ────────────────────────────────────────────────────────────────

    @Test
    fun `undoing a partial use puts the units back`() = runTest {
        val id = repository.add(sampleItem(quantity = 3))
        repository.use(id, amount = 2)

        assertTrue(repository.undoLastResolution(id))

        assertEquals(3, itemDao.rows.getValue(id).quantity)
        assertEquals(ItemState.ACTIVE, itemDao.rows.getValue(id).state)
    }

    @Test
    fun `undoing a full use makes the item active again`() = runTest {
        val id = repository.add(sampleItem(quantity = 2))
        repository.use(id, amount = 2)

        assertTrue(repository.undoLastResolution(id))

        val row = itemDao.rows.getValue(id)
        assertEquals(ItemState.ACTIVE, row.state)
        assertNull(row.resolvedAt)
        assertEquals(2, row.quantity)
    }

    @Test
    fun `undo records a reversal rather than deleting the original`() = runTest {
        val id = repository.add(sampleItem(quantity = 3))
        repository.use(id, amount = 1)
        val use = eventDao.events.single { it.type == ItemEventType.QUANTITY_USED }

        repository.undoLastResolution(id)

        // The use did happen and then was corrected. Erasing it would leave a
        // ledger that disagrees with its own history.
        assertTrue(eventDao.events.any { it.id == use.id })
        val reversal = eventDao.events.single { it.type == ItemEventType.ITEM_RESTORED }
        assertEquals(use.id, reversal.reversesEventId)
        assertEquals(ItemEventType.QUANTITY_USED, reversal.reversesEventType)
        assertEquals(1, reversal.quantity)
    }

    @Test
    fun `an undone use stops counting towards impact`() = runTest {
        val id = repository.add(sampleItem(quantity = 3))
        repository.use(id, amount = 2)
        assertEquals(2, repository.observeImpact().first().itemsSaved)

        repository.undoLastResolution(id)

        assertEquals(0, repository.observeImpact().first().itemsSaved)
    }

    @Test
    fun `an undone discard stops counting as waste`() = runTest {
        val id = repository.add(sampleItem(quantity = 2))
        repository.discard(id, amount = 1)
        assertEquals(1, repository.observeImpact().first().itemsWasted)

        repository.undoLastResolution(id)

        assertEquals(0, repository.observeImpact().first().itemsWasted)
    }

    @Test
    fun `the same resolution cannot be undone twice`() = runTest {
        val id = repository.add(sampleItem(quantity = 3))
        repository.use(id, amount = 1)

        assertTrue(repository.undoLastResolution(id))
        // A second tap must not keep crediting quantity that was never taken.
        assertFalse(repository.undoLastResolution(id))
        assertEquals(3, itemDao.rows.getValue(id).quantity)
    }

    @Test
    fun `undo steps back one resolution at a time`() = runTest {
        val id = repository.add(sampleItem(quantity = 5))
        repository.use(id, amount = 1)
        repository.use(id, amount = 2)

        repository.undoLastResolution(id)

        assertEquals(4, itemDao.rows.getValue(id).quantity)
        assertEquals(1, repository.observeImpact().first().itemsSaved)
    }

    @Test
    fun `undo reports nothing to do when there is no resolution`() = runTest {
        val id = repository.add(sampleItem())

        assertFalse(repository.undoLastResolution(id))
    }

    @Test
    fun `undo queues a sync operation like any other change`() = runTest {
        val id = repository.add(sampleItem(quantity = 2))
        repository.use(id, amount = 1)
        outboxDao.operations.clear()

        repository.undoLastResolution(id)

        assertEquals(
            OutboxOperationType.RESTORE,
            outboxDao.operations.single().operationType
        )
    }

    @Test
    fun `a use with a non-positive amount is rejected`() = runTest {
        val id = repository.add(sampleItem())

        val error = runCatching { repository.use(id, amount = 0) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
    }
}
