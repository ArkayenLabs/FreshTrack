package com.example.freshtrack.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** The shape of a pending change, matching the shared sync-operation contract. */
enum class OutboxOperationType {
    CREATE,
    UPDATE,
    TOMBSTONE,
    RESTORE,
    EVENT_APPEND
}

/**
 * A local change waiting to reach the server.
 *
 * The previous sync worked from a high-water mark: "push everything modified
 * since timestamp T". That has two faults this replaces. A row pulled from the
 * server was immediately newer than the mark and got pushed straight back,
 * costing a redundant write per pulled row every cycle. And because the mark
 * advanced on success, a failure in the middle of a batch could move it past
 * changes that never actually went up.
 *
 * An explicit queue makes the pending set a fact rather than an inference: a
 * row is here because it has not been acknowledged, and it leaves only when it
 * has. Entries are written in the same transaction as the change they describe,
 * so a change can never exist locally without being queued.
 */
@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["kitchenId", "clientSequence"]),
        Index(value = ["entityId"])
    ]
)
data class OutboxEntity(
    /**
     * Idempotency key. The server must treat a repeat of this id as a no-op, so
     * that a push whose acknowledgement was lost can safely be retried.
     */
    @PrimaryKey
    val operationId: String,

    val entityId: String,

    val kitchenId: String,

    val actorUid: String,

    /** Identifies this installation, so a device can recognise its own writes. */
    val clientId: String,

    /** Monotonic per client. Preserves local ordering independent of clocks. */
    val clientSequence: Long,

    /**
     * The server revision this change was made against, or null for a create.
     * Lets the server detect that two devices edited the same version.
     */
    val baseRevision: Long? = null,

    val operationType: OutboxOperationType,

    val occurredAtClient: Long,

    /** Serialised payload for the operation. */
    val payload: String,

    val schemaVersion: Int = CURRENT_ITEM_SCHEMA_VERSION,

    /** Retry bookkeeping, so a permanently failing entry can be surfaced. */
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
    val lastError: String? = null
)
