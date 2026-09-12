package com.example.freshtrack.data.sync

import com.example.freshtrack.data.local.entities.GUEST_USER_ID
import com.example.freshtrack.data.local.entities.ItemEntity
import com.example.freshtrack.data.local.entities.ItemEventEntity
import com.example.freshtrack.data.local.entities.ItemEventType
import com.example.freshtrack.data.local.entities.LOCAL_KITCHEN_ID
import com.example.freshtrack.data.local.entities.OutboxEntity
import com.example.freshtrack.data.local.entities.OutboxOperationType
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
    private val eventDao = FakeItemEventDao()
    private val remote = FakeRemoteStore()
    private val clock = object : AppClock {
        override fun nowMillis() = 1_000L
        override fun today(): LocalDate = LocalDate.of(2026, 9, 12)
    }

    private fun pusher(batchSize: Int = 50, stuckThreshold: Int = 5) = OutboxPusher(
        outboxDao, eventDao, remote, OutboxPayload::deserialise, clock, batchSize, stuckThreshold
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

/** A scripted server: says whether the kitchen is premium, and fails the pushes it is told to. */
private class FakeRemoteStore : RemoteStore {
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

    override suspend fun deleteAccountData(kitchenId: String, uid: String) = Result.success(Unit)
}
