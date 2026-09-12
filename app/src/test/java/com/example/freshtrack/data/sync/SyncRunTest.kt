package com.example.freshtrack.data.sync

import com.example.freshtrack.data.local.TransactionRunner
import com.example.freshtrack.data.repository.FakeItemDao
import com.example.freshtrack.data.repository.FakeItemEventDao
import com.example.freshtrack.data.repository.FakeOutboxDao
import com.example.freshtrack.data.session.KitchenSession
import com.example.freshtrack.util.AppClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * One sync run, end to end through the fakes: the kitchen document, the
 * push, the pull, and what gets recorded for the Settings card.
 */
class SyncRunTest {

    private val session = object : KitchenSession {
        var signedIn = true
        override fun currentUserId() = if (signedIn) "alice" else "guest"
        override fun isSignedIn() = signedIn
        override fun activeKitchenId() = if (signedIn) "personal-alice" else "local"
    }
    private val outboxDao = FakeOutboxDao()
    private val itemDao = FakeItemDao()
    private val eventDao = FakeItemEventDao()
    private val remote = FakeRemoteStore()
    private val syncState = FakeSyncState().apply { markBootstrapped("personal-alice") }
    private val clock = object : AppClock {
        override fun nowMillis() = 5_000L
        override fun today(): LocalDate = LocalDate.of(2026, 9, 12)
    }
    private val transactions = object : TransactionRunner {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }

    private fun run() = SyncRun(
        session = session,
        remote = remote,
        pusher = OutboxPusher(outboxDao, itemDao, eventDao, remote, syncState, "device-a", OutboxPayload::deserialise, clock),
        applier = RemoteChangeApplier(itemDao, eventDao, remote, syncState, transactions, "device-a"),
        syncState = syncState,
        clock = clock
    )

    @Test
    fun `signed out, nothing is attempted and nothing is recorded`() = runTest {
        session.signedIn = false

        assertEquals(SyncRun.Result.SIGNED_OUT, run().run())
        assertEquals(0, remote.entitlementReads)
        assertEquals(0, remote.fetches)
        assertNull(remote.ensuredKitchen)
        assertNull(syncState.lastRun())
    }

    @Test
    fun `the kitchen document is made sure of before anything is sent`() = runTest {
        run().run()

        assertEquals("personal-alice" to "alice", remote.ensuredKitchen)
    }

    @Test
    fun `a clean run is recorded as synced, with the time`() = runTest {
        assertEquals(SyncRun.Result.SYNCED, run().run())
        assertEquals(SyncRun.Result.SYNCED, syncState.lastRun())
        assertEquals(5_000L, syncState.lastSuccessAt())
    }

    @Test
    fun `a free kitchen is recorded as not entitled, and the time is not moved`() = runTest {
        remote.premium = false
        // Something queued, so the entitlement is actually consulted.
        outboxDao.enqueue(
            com.example.freshtrack.data.local.entities.OutboxEntity(
                operationId = "op-1", entityId = "x", kitchenId = "personal-alice", actorUid = "alice",
                clientId = "device-a", clientSequence = 1,
                operationType = com.example.freshtrack.data.local.entities.OutboxOperationType.CREATE,
                occurredAtClient = 1, payload = "{}"
            )
        )

        assertEquals(SyncRun.Result.NOT_ENTITLED, run().run())
        assertEquals(SyncRun.Result.NOT_ENTITLED, syncState.lastRun())
        assertEquals(0L, syncState.lastSuccessAt())
    }

    @Test
    fun `a pull that fails defers the run but the push still happened`() = runTest {
        remote.fetchError = RemoteError.Transient()

        assertEquals(SyncRun.Result.DEFERRED, run().run())
        assertEquals(SyncRun.Result.DEFERRED, syncState.lastRun())
        assertEquals(0L, syncState.lastSuccessAt())
        assertTrue(remote.entitlementReads <= 1)
    }

    @Test
    fun `a kitchen that cannot be created defers, without touching the queue`() = runTest {
        remote.ensureError = RemoteError.Transient()

        assertEquals(SyncRun.Result.DEFERRED, run().run())
        assertEquals(0, remote.entitlementReads)
    }
}
