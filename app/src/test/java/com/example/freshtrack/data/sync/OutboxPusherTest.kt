package com.example.freshtrack.data.sync

import com.example.freshtrack.data.local.entities.GUEST_USER_ID
import com.example.freshtrack.data.local.entities.ItemEntity
import com.example.freshtrack.data.local.entities.ItemEventEntity
import com.example.freshtrack.data.local.entities.ItemEventType
import com.example.freshtrack.data.local.entities.ItemState
import com.example.freshtrack.data.local.entities.LOCAL_KITCHEN_ID
import com.example.freshtrack.data.local.entities.OutboxEntity
import com.example.freshtrack.data.local.entities.OutboxOperationType
import com.example.freshtrack.data.repository.FakeItemDao
import com.example.freshtrack.data.repository.FakeItemEventDao
import com.example.freshtrack.data.repository.FakeOutboxDao
import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.DateSource
import com.example.freshtrack.util.AppClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The push half of the transport, one branch of `sync-design.md` §5 per test.
 *
 * The property under test throughout is that nothing leaves the queue except
 * on acknowledgement, and nothing is dropped.
 */
class OutboxPusherTest {

    private val kitchen = "personal-alice"
    private val outboxDao = FakeOutboxDao()
    private val itemDao = FakeItemDao()
    private val eventDao = FakeItemEventDao()
    private val remote = FakeRemoteStore()
    // Most tests are about the incremental path, so they start already backed up.
    private val syncState = FakeSyncState().apply { markBootstrapped("personal-alice") }
    private val clock = object : AppClock {
        override fun nowMillis() = 1_000L
        override fun today(): LocalDate = LocalDate.of(2026, 9, 12)
    }

    private fun pusher(batchSize: Int = 50, stuckThreshold: Int = 5) = OutboxPusher(
        outboxDao, itemDao, eventDao, remote, syncState, "device-a", OutboxPayload::deserialise, clock, batchSize, stuckThreshold
    )

    /** Queues one change the way ItemRepositoryImpl.record does: row snapshot plus event. */
    private suspend fun queue(
        operationId: String,
        sequence: Long,
        itemId: String = "item-$sequence",
        snapshotKitchen: String = kitchen,
        actorUid: String = "alice",
        attempts: Int = 0
    ) {
        val row = ItemEntity(
            id = itemId,
            kitchenId = snapshotKitchen,
            name = "Milk $sequence",
            category = "Dairy & Eggs",
            expiryDate = LocalDate.of(2026, 9, 20),
            dateKind = DateKind.USE_BY,
            dateSource = DateSource.USER,
            dateConfidence = 1f,
            createdBy = if (snapshotKitchen == LOCAL_KITCHEN_ID) GUEST_USER_ID else actorUid
        )
        itemDao.insert(row.copy(kitchenId = kitchen))
        eventDao.append(
            ItemEventEntity(
                id = "evt-$operationId", itemId = itemId, kitchenId = kitchen,
                type = ItemEventType.ITEM_CREATED, actorUid = actorUid,
                occurredAt = 100L, operationId = operationId
            )
        )
        outboxDao.enqueue(
            OutboxEntity(
                operationId = operationId, entityId = itemId, kitchenId = kitchen,
                actorUid = actorUid, clientId = "device-a", clientSequence = sequence,
                operationType = OutboxOperationType.CREATE, occurredAtClient = 100L,
                payload = OutboxPayload.serialise(row), attemptCount = attempts
            )
        )
    }

    // ─── Nothing to do ──────────────────────────────────────────────────────

    @Test
    fun `an empty queue costs no remote call at all`() = runTest {
        val outcome = pusher().push(kitchen)

        assertEquals(OutboxPusher.Outcome.Drained(0), outcome)
        assertEquals(0, remote.entitlementReads)
    }

    // ─── Entitlement ────────────────────────────────────────────────────────

    @Test
    fun `a free kitchen is not pushed, and the queue is left as it was`() = runTest {
        queue("op-1", 1)
        remote.premium = false

        val outcome = pusher().push(kitchen)

        assertEquals(OutboxPusher.Outcome.NotEntitled(0), outcome)
        assertEquals(1, outboxDao.operations.size)
        assertEquals(0, outboxDao.operations.single().attemptCount)
        assertTrue(remote.pushes.isEmpty())
    }

    @Test
    fun `an entitlement read that fails defers, and touches nothing`() = runTest {
        queue("op-1", 1)
        remote.premiumError = RemoteError.Transient()

        val outcome = pusher().push(kitchen)

        assertTrue(outcome is OutboxPusher.Outcome.Deferred)
        assertEquals(0, outboxDao.operations.single().attemptCount)
    }

    // ─── The happy path ─────────────────────────────────────────────────────

    @Test
    fun `changes go up oldest first and leave the queue on acknowledgement`() = runTest {
        queue("op-2", 2)
        queue("op-1", 1)

        val outcome = pusher().push(kitchen)

        assertEquals(OutboxPusher.Outcome.Drained(2), outcome)
        assertEquals(listOf("op-1", "op-2"), remote.pushes.map { it.operationId })
        assertTrue(outboxDao.operations.isEmpty())
    }

    @Test
    fun `the write carries the snapshot, the event, and the operation id`() = runTest {
        queue("op-1", 1, itemId = "milk")

        pusher().push(kitchen)

        val write = remote.pushes.single()
        assertEquals(kitchen, write.kitchenId)
        assertEquals("milk", write.itemId)
        assertEquals("Milk 1", write.itemFields["name"])
        assertEquals("2026-09-20", write.itemFields["expiryDate"])
        assertEquals("op-1", write.itemFields["lastOperationId"])
        assertEquals("ITEM_CREATED", write.eventFields["type"])
        assertEquals("op-1", write.eventFields["operationId"])
    }

    @Test
    fun `a batch larger than the page size is drained in one run`() = runTest {
        (1L..7L).forEach { queue("op-$it", it) }

        val outcome = pusher(batchSize = 3).push(kitchen)

        assertEquals(OutboxPusher.Outcome.Drained(7), outcome)
        assertTrue(outboxDao.operations.isEmpty())
    }

    // ─── The claim, on the wire ─────────────────────────────────────────────

    @Test
    fun `a snapshot taken before sign-in goes to the claimed kitchen under the claimed actor`() = runTest {
        // The payload says "local" and "guest"; the outbox row was rewritten
        // by the claim. The row wins, and the kitchen is the path, not a field.
        queue("op-1", 1, snapshotKitchen = LOCAL_KITCHEN_ID, actorUid = "alice")

        pusher().push(kitchen)

        val write = remote.pushes.single()
        assertEquals(kitchen, write.kitchenId)
        assertFalse(write.itemFields.containsKey("kitchenId"))
        assertEquals("alice", write.itemFields["createdBy"])
    }

    // ─── Failures ───────────────────────────────────────────────────────────

    @Test
    fun `a temporary failure is recorded on that entry and stops the run`() = runTest {
        queue("op-1", 1)
        queue("op-2", 2)
        remote.failWith["op-1"] = RemoteError.Transient()

        val outcome = pusher().push(kitchen)

        assertTrue(outcome is OutboxPusher.Outcome.Deferred)
        assertEquals(0, (outcome as OutboxPusher.Outcome.Deferred).pushed)
        val first = outboxDao.operations.first { it.operationId == "op-1" }
        assertEquals(1, first.attemptCount)
        assertEquals(1_000L, first.lastAttemptAt)
        // The one behind it was not attempted, so it carries no failure.
        assertEquals(0, outboxDao.operations.first { it.operationId == "op-2" }.attemptCount)
        assertEquals(listOf("op-1"), remote.pushes.map { it.operationId })
    }

    @Test
    fun `a permanent failure is recorded and the rest of the queue still goes`() = runTest {
        queue("op-1", 1)
        queue("op-2", 2)
        remote.failWith["op-1"] = RemoteError.Permanent()

        val outcome = pusher().push(kitchen)

        assertEquals(OutboxPusher.Outcome.Drained(1), outcome)
        assertEquals(listOf("op-1"), outboxDao.operations.map { it.operationId })
        assertEquals(1, outboxDao.operations.single().attemptCount)
    }

    @Test
    fun `a permanently failing entry is attempted once per run, not once per batch`() = runTest {
        queue("op-1", 1)
        (2L..6L).forEach { queue("op-$it", it) }
        remote.failWith["op-1"] = RemoteError.Permanent()

        pusher(batchSize = 2).push(kitchen)

        assertEquals(1, outboxDao.operations.single().attemptCount)
    }

    @Test
    fun `a stuck entry is skipped, surfaced, and never dropped`() = runTest {
        queue("op-1", 1, attempts = 5)
        queue("op-2", 2)

        val outcome = pusher().push(kitchen)

        assertEquals(OutboxPusher.Outcome.Drained(1), outcome)
        assertEquals(listOf("op-2"), remote.pushes.map { it.operationId })
        assertEquals(listOf("op-1"), outboxDao.getStuck(kitchen, 5).map { it.operationId })
    }

    @Test
    fun `an entry with no event is corruption, recorded rather than pushed`() = runTest {
        queue("op-1", 1)
        eventDao.events.clear()

        pusher().push(kitchen)

        assertTrue(remote.pushes.isEmpty())
        assertEquals("no event for operation", outboxDao.operations.single().lastError)
    }

    // ─── Idempotency ────────────────────────────────────────────────────────

    @Test
    fun `a retry the server already has is acknowledged, not counted as refused`() = runTest {
        // A lost acknowledgement: the event landed, the queue never heard.
        // The rules refuse the repeat because events cannot be updated; the
        // existence read tells the pusher that is what happened.
        queue("op-1", 1)
        remote.failWith["op-1"] = RemoteError.PermissionDenied()
        remote.existing += "op-1"

        val outcome = pusher().push(kitchen)

        assertEquals(OutboxPusher.Outcome.Drained(1), outcome)
        assertTrue(outboxDao.operations.isEmpty())
    }

    @Test
    fun `a refusal with no event behind it is an entitlement problem, and stops`() = runTest {
        // The entitlement read said premium, but the write was refused and
        // nothing is there: the kitchen lapsed between the two. Stop, leave
        // the entry untouched, and let the next run re-read the entitlement.
        queue("op-1", 1)
        queue("op-2", 2)
        remote.failWith["op-1"] = RemoteError.PermissionDenied()

        val outcome = pusher().push(kitchen)

        assertEquals(OutboxPusher.Outcome.NotEntitled(0), outcome)
        assertEquals(2, outboxDao.operations.size)
        assertTrue(outboxDao.operations.all { it.attemptCount == 0 })
    }
}

// ─── Conflicts ──────────────────────────────────────────────────────────────

/**
 * Two devices change the same item offline. Row edits are last write wins;
 * quantity events are deltas and are rebased onto what the server has, so the
 * shelf ends up agreeing with the ledger.
 */
class OutboxPusherRebaseTest {

    private val kitchen = "personal-alice"
    private val outboxDao = FakeOutboxDao()
    private val itemDao = FakeItemDao()
    private val eventDao = FakeItemEventDao()
    private val remote = FakeRemoteStore()
    private val syncState = FakeSyncState().apply { markBootstrapped("personal-alice") }
    private val clock = object : AppClock {
        override fun nowMillis() = 1_000L
        override fun today(): LocalDate = LocalDate.of(2026, 9, 12)
    }

    private fun pusher() = OutboxPusher(
        outboxDao, itemDao, eventDao, remote, syncState, "device-a", OutboxPayload::deserialise, clock
    )

    private fun row(quantity: Int, revision: Long, state: ItemState = ItemState.ACTIVE) = ItemEntity(
        id = "milk", kitchenId = kitchen, name = "Milk", category = "Dairy & Eggs",
        expiryDate = LocalDate.of(2026, 9, 20), dateKind = DateKind.USE_BY,
        dateSource = DateSource.USER, dateConfidence = 1f, createdBy = "alice",
        lastEditedBy = "alice", quantity = quantity, originalQuantity = 3,
        revision = revision, state = state
    )

    /** This device took [used] of the milk while its row was at [base] on the server. */
    private suspend fun queueUse(used: Int, base: Long, type: ItemEventType = ItemEventType.QUANTITY_USED) {
        val after = row(quantity = 3 - used, revision = base)
        itemDao.insert(after)
        eventDao.append(
            ItemEventEntity(
                id = "evt-a", itemId = "milk", kitchenId = kitchen, type = type,
                actorUid = "alice", quantity = used, occurredAt = 200L, operationId = "op-a"
            )
        )
        outboxDao.enqueue(
            OutboxEntity(
                operationId = "op-a", entityId = "milk", kitchenId = kitchen, actorUid = "alice",
                clientId = "device-a", clientSequence = 1, baseRevision = base,
                operationType = OutboxOperationType.UPDATE, occurredAtClient = 200L,
                payload = OutboxPayload.serialise(after)
            )
        )
    }

    /** What the server holds: [quantity] left, written by [by] at [at]. */
    private fun serverHas(quantity: Int, at: Long, by: String = "device-b") {
        remote.serverItems += RemoteDocument(
            "milk", WireFormat.item(row(quantity, revision = 0), "op-$by", "bob", by), at
        )
    }

    @Test
    fun `a use pushed against a row nobody else touched goes up as taken`() = runTest {
        serverHas(quantity = 3, at = 100, by = "device-a")
        queueUse(used = 1, base = 100)

        pusher().push(kitchen)

        assertEquals(2, remote.pushes.single().itemFields["quantity"])
        assertEquals(2, itemDao.rows.getValue("milk").quantity)
    }

    @Test
    fun `a use pushed against a row someone else used from is rebased, not written over`() = runTest {
        // Both saw 3. Bob used one and his push landed first; the server says
        // 2. Alice's snapshot also says 2. Writing it would lose Bob's use.
        serverHas(quantity = 2, at = 500)
        queueUse(used = 1, base = 100)

        pusher().push(kitchen)

        assertEquals(1, remote.pushes.single().itemFields["quantity"])
        // The local row agrees with what was pushed, and now stands at the
        // server's revision: it has Bob's change in it, so the pull that
        // follows must not treat Bob's document as news and write it back.
        val local = itemDao.rows.getValue("milk")
        assertEquals(1, local.quantity)
        assertEquals(500L, local.revision)
        assertEquals(ItemState.ACTIVE, local.state)
        // Reconciling is not an event. The ledger has Alice's use, and will
        // get Bob's on pull; a third entry would count something that did not happen.
        assertEquals(1, eventDao.events.size)
    }

    @Test
    fun `a rebase that leaves nothing resolves the item the way the repository would`() = runTest {
        serverHas(quantity = 1, at = 500)
        queueUse(used = 2, base = 100, type = ItemEventType.QUANTITY_DISCARDED)

        pusher().push(kitchen)

        val pushed = remote.pushes.single().itemFields
        assertEquals("DISCARDED", pushed["state"])
        assertEquals(200L, pushed["resolvedAt"])
        assertEquals(ItemState.DISCARDED, itemDao.rows.getValue("milk").state)
    }

    @Test
    fun `an edit that is not a quantity event is last write wins, and reads nothing first`() = runTest {
        serverHas(quantity = 2, at = 500)
        val edited = row(quantity = 3, revision = 100).copy(name = "Whole milk")
        itemDao.insert(edited)
        eventDao.append(
            ItemEventEntity(
                id = "evt-a", itemId = "milk", kitchenId = kitchen, type = ItemEventType.ITEM_EDITED,
                actorUid = "alice", occurredAt = 200L, operationId = "op-a"
            )
        )
        outboxDao.enqueue(
            OutboxEntity(
                operationId = "op-a", entityId = "milk", kitchenId = kitchen, actorUid = "alice",
                clientId = "device-a", clientSequence = 1, baseRevision = 100,
                operationType = OutboxOperationType.UPDATE, occurredAtClient = 200L,
                payload = OutboxPayload.serialise(edited)
            )
        )

        pusher().push(kitchen)

        assertEquals(0, remote.itemFetches)
        assertEquals("Whole milk", remote.pushes.single().itemFields["name"])
    }

    @Test
    fun `a create is never rebased`() = runTest {
        // No base revision: there was nothing on the server to conflict with.
        val fresh = row(quantity = 3, revision = 0)
        itemDao.insert(fresh)
        eventDao.append(
            ItemEventEntity(
                id = "evt-a", itemId = "milk", kitchenId = kitchen, type = ItemEventType.QUANTITY_USED,
                actorUid = "alice", quantity = 1, occurredAt = 200L, operationId = "op-a"
            )
        )
        outboxDao.enqueue(
            OutboxEntity(
                operationId = "op-a", entityId = "milk", kitchenId = kitchen, actorUid = "alice",
                clientId = "device-a", clientSequence = 1, baseRevision = null,
                operationType = OutboxOperationType.CREATE, occurredAtClient = 200L,
                payload = OutboxPayload.serialise(fresh)
            )
        )

        pusher().push(kitchen)

        assertEquals(0, remote.itemFetches)
    }

    @Test
    fun `if the server cannot be read the push waits rather than guesses`() = runTest {
        queueUse(used = 1, base = 100)
        remote.fetchError = RemoteError.Transient()

        val outcome = pusher().push(kitchen)

        assertTrue(outcome is OutboxPusher.Outcome.Deferred)
        assertTrue(remote.pushes.isEmpty())
        assertEquals(1, outboxDao.operations.size)
    }
}

// ─── First backup ───────────────────────────────────────────────────────────

class OutboxPusherBootstrapTest {

    private val kitchen = "personal-alice"
    private val outboxDao = FakeOutboxDao()
    private val itemDao = FakeItemDao()
    private val eventDao = FakeItemEventDao()
    private val remote = FakeRemoteStore()
    private val syncState = FakeSyncState()
    private val clock = object : AppClock {
        override fun nowMillis() = 1_000L
        override fun today(): LocalDate = LocalDate.of(2026, 9, 12)
    }

    private fun pusher() = OutboxPusher(
        outboxDao, itemDao, eventDao, remote, syncState, "device-a", OutboxPayload::deserialise, clock
    )

    private suspend fun history(itemId: String, sequence: Long, deleted: Boolean = false) {
        itemDao.insert(
            ItemEntity(
                id = itemId, kitchenId = kitchen, name = itemId, category = "Dairy & Eggs",
                expiryDate = LocalDate.of(2026, 9, 20), dateKind = DateKind.USE_BY,
                dateSource = DateSource.USER, dateConfidence = 1f, createdBy = "alice",
                lastEditedBy = "alice", isDeleted = deleted
            )
        )
        eventDao.append(
            ItemEventEntity(
                id = "evt-$sequence", itemId = itemId, kitchenId = kitchen,
                type = ItemEventType.ITEM_CREATED, actorUid = "alice",
                occurredAt = sequence, operationId = "op-$sequence"
            )
        )
        outboxDao.enqueue(
            OutboxEntity(
                operationId = "op-$sequence", entityId = itemId, kitchenId = kitchen,
                actorUid = "alice", clientId = "device-a", clientSequence = sequence,
                operationType = OutboxOperationType.CREATE, occurredAtClient = sequence,
                payload = "{}"
            )
        )
    }

    @Test
    fun `a first backup uploads every row and the whole ledger, and empties the queue`() = runTest {
        history("milk", 1)
        history("eggs", 2, deleted = true)

        val outcome = pusher().push(kitchen)

        assertEquals(OutboxPusher.Outcome.Drained(0), outcome)
        assertEquals(setOf("milk", "eggs"), remote.bootstrappedItems.keys)
        assertEquals(true, remote.bootstrappedItems.getValue("eggs")["isDeleted"])
        assertEquals(listOf("op-1", "op-2"), remote.bootstrappedEvents)
        assertTrue("the queue was superseded by the upload", outboxDao.operations.isEmpty())
        assertTrue(syncState.isBootstrapped(kitchen))
        // Nothing went through the incremental path.
        assertTrue(remote.pushes.isEmpty())
    }

    @Test
    fun `a kitchen with rows but an empty queue is still backed up`() = runTest {
        history("milk", 1)
        outboxDao.operations.clear()

        pusher().push(kitchen)

        assertEquals(setOf("milk"), remote.bootstrappedItems.keys)
        assertTrue(syncState.isBootstrapped(kitchen))
    }

    @Test
    fun `a free kitchen is not backed up`() = runTest {
        history("milk", 1)
        remote.premium = false

        val outcome = pusher().push(kitchen)

        assertEquals(OutboxPusher.Outcome.NotEntitled(0), outcome)
        assertTrue(remote.bootstrappedItems.isEmpty())
        assertFalse(syncState.isBootstrapped(kitchen))
        assertEquals(1, outboxDao.operations.size)
    }

    @Test
    fun `an interrupted ledger upload resumes after the last batch that landed`() = runTest {
        (1L..1200L).forEach { history("item-$it", it) }
        remote.failEventBatch = 2   // the second batch of 500 fails, temporarily

        val first = pusher().push(kitchen)

        assertTrue(first is OutboxPusher.Outcome.Deferred)
        assertEquals(500, syncState.bootstrapEventsUploaded(kitchen))
        assertFalse(syncState.isBootstrapped(kitchen))
        assertEquals("nothing is dropped until the backup completes", 1200, outboxDao.operations.size)

        remote.failEventBatch = null
        val second = pusher().push(kitchen)

        assertEquals(OutboxPusher.Outcome.Drained(0), second)
        assertEquals(1200, remote.bootstrappedEvents.size)
        assertEquals("every event exactly once", 1200, remote.bootstrappedEvents.toSet().size)
        assertTrue(syncState.isBootstrapped(kitchen))
        assertTrue(outboxDao.operations.isEmpty())
    }

    @Test
    fun `a batch that landed before the crash is not sent again`() = runTest {
        // The previous run committed the first batch and died before recording
        // it. The server refuses the repeat; the existence read says why, and
        // the count moves past it.
        (1L..600L).forEach { history("item-$it", it) }
        remote.existing += "op-1"
        remote.refuseEventBatchesContaining += "op-1"

        val outcome = pusher().push(kitchen)

        assertEquals(OutboxPusher.Outcome.Drained(0), outcome)
        assertEquals(600, syncState.bootstrapEventsUploaded(kitchen))
        // Only the second batch actually went up this run.
        assertEquals(100, remote.bootstrappedEvents.size)
        assertTrue(syncState.isBootstrapped(kitchen))
    }

    @Test
    fun `a change made during the backup still goes up on its own`() = runTest {
        history("milk", 1)
        // Something queued after the upload started: a higher sequence than
        // anything the upload could have seen.
        remote.onBootstrapItems = {
            outboxDao.enqueue(
                OutboxEntity(
                    operationId = "op-late", entityId = "milk", kitchenId = kitchen,
                    actorUid = "alice", clientId = "device-a", clientSequence = 99,
                    operationType = OutboxOperationType.UPDATE, occurredAtClient = 200,
                    payload = OutboxPayload.serialise(itemDao.rows.getValue("milk"))
                )
            )
            eventDao.append(
                ItemEventEntity(
                    id = "evt-late", itemId = "milk", kitchenId = kitchen,
                    type = ItemEventType.ITEM_EDITED, actorUid = "alice",
                    occurredAt = 200, operationId = "op-late"
                )
            )
        }

        val outcome = pusher().push(kitchen)

        assertEquals(OutboxPusher.Outcome.Drained(1), outcome)
        assertEquals(listOf("op-late"), remote.pushes.map { it.operationId })
        assertTrue(outboxDao.operations.isEmpty())
    }
}
