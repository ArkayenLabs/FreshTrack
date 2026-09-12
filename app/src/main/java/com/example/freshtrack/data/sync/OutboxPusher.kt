package com.example.freshtrack.data.sync

import com.example.freshtrack.data.local.dao.ItemEventDao
import com.example.freshtrack.data.local.dao.OutboxDao
import com.example.freshtrack.data.local.entities.ItemEntity
import com.example.freshtrack.data.local.entities.OutboxEntity
import com.example.freshtrack.util.AppClock

/**
 * Drains one kitchen's outbox to the server, oldest change first.
 *
 * This is the push half of `sync-design.md` §5. It knows nothing about
 * Firestore or WorkManager: it takes DAOs, a [RemoteStore] and a clock, and
 * returns what happened so the worker can decide whether to back off. That is
 * what lets every branch below be exercised on the JVM.
 *
 * An entry leaves the queue only on acknowledgement. A change that could not
 * be sent stays, with its failure recorded, until it either goes or has failed
 * often enough to be shown to the person as stuck. Nothing is ever dropped.
 */
class OutboxPusher(
    private val outboxDao: OutboxDao,
    private val eventDao: ItemEventDao,
    private val remote: RemoteStore,
    private val deserialise: (String) -> ItemEntity,
    private val clock: AppClock,
    private val batchSize: Int = 50,
    private val stuckThreshold: Int = STUCK_THRESHOLD
) {

    sealed interface Outcome {
        /** The queue is empty, or everything left in it is stuck. */
        data class Drained(val pushed: Int) : Outcome

        /** The server says this kitchen may not write. Expected for a free account. */
        data class NotEntitled(val pushed: Int) : Outcome

        /** Something temporary went wrong; the rest of the queue waits. */
        data class Deferred(val pushed: Int, val error: RemoteError) : Outcome
    }

    suspend fun push(kitchenId: String): Outcome {
        var pushed = 0

        // The entitlement read is skipped when there is nothing to send, so an
        // idle free account costs nothing.
        if (outboxDao.peek(kitchenId, 1).isEmpty()) return Outcome.Drained(0)

        val premium = remote.isKitchenPremium(kitchenId)
            .getOrElse { return Outcome.Deferred(0, it.asRemoteError()) }
        if (!premium) return Outcome.NotEntitled(0)

        // An entry that failed permanently gets one attempt per run, not one
        // per batch: retrying it inside the same run would burn through its
        // attempts in seconds and call it stuck before anything had changed.
        val failedThisRun = mutableSetOf<String>()

        while (true) {
            val batch = outboxDao.peek(kitchenId, batchSize)
                .filter { it.attemptCount < stuckThreshold && it.operationId !in failedThisRun }
            if (batch.isEmpty()) return Outcome.Drained(pushed)

            for (op in batch) {
                when (val result = pushOne(op)) {
                    PushResult.Sent -> pushed++
                    PushResult.Failed -> failedThisRun += op.operationId
                    is PushResult.Stop -> return when (result.error) {
                        is RemoteError.PermissionDenied -> Outcome.NotEntitled(pushed)
                        else -> Outcome.Deferred(pushed, result.error)
                    }
                }
            }
        }
    }

    private sealed interface PushResult {
        data object Sent : PushResult
        data object Failed : PushResult
        data class Stop(val error: RemoteError) : PushResult
    }

    private suspend fun pushOne(op: OutboxEntity): PushResult {
        // The event is the outbox entry's other half. It cannot be missing —
        // both are written in one transaction — so its absence is corruption,
        // recorded as such rather than pushed as a row with no history.
        val event = eventDao.findByOperationId(op.operationId)
            ?: return fail(op, "no event for operation")

        val write = RemoteWrite(
            kitchenId = op.kitchenId,
            itemId = op.entityId,
            itemFields = WireFormat.item(deserialise(op.payload), op),
            operationId = op.operationId,
            eventFields = WireFormat.event(event)
        )

        val result = remote.push(write)
        if (result.isSuccess) {
            outboxDao.acknowledge(listOf(op.operationId))
            return PushResult.Sent
        }

        return when (val error = result.exceptionOrNull()!!.asRemoteError()) {
            is RemoteError.PermissionDenied -> {
                // Refused either because the event is already there — a retry
                // after a lost acknowledgement, which the rules refuse because
                // events cannot be updated — or because the kitchen may not
                // write. One read tells them apart.
                val exists = remote.eventExists(op.kitchenId, op.operationId)
                    .getOrElse { return PushResult.Stop(it.asRemoteError()) }
                if (exists) {
                    outboxDao.acknowledge(listOf(op.operationId))
                    PushResult.Sent
                } else {
                    PushResult.Stop(error)
                }
            }

            is RemoteError.Transient -> {
                outboxDao.recordFailure(op.operationId, clock.nowMillis(), error.message)
                PushResult.Stop(error)
            }

            is RemoteError.Permanent -> fail(op, error.message)
        }
    }

    private suspend fun fail(
        op: OutboxEntity,
        reason: String?
    ): PushResult {
        outboxDao.recordFailure(op.operationId, clock.nowMillis(), reason)
        return PushResult.Failed
    }

    private fun Throwable.asRemoteError(): RemoteError =
        this as? RemoteError ?: RemoteError.Permanent(this)

    companion object {
        /** Attempts after which an entry is shown as stuck rather than retried. */
        const val STUCK_THRESHOLD = 5
    }
}
