package com.example.freshtrack.data.local

import androidx.room.withTransaction

/**
 * Runs a block atomically.
 *
 * Every mutation in this app writes in three places — the item, the event
 * ledger, and the outbox — and all three have to land or none of them. A use
 * recorded without its event silently loses history; an event without its
 * outbox entry never reaches another device.
 *
 * Behind an interface so repository logic can be tested on the JVM without a
 * real database, since the interesting part is what gets written together, not
 * SQLite's transaction implementation.
 */
interface TransactionRunner {
    suspend fun <T> run(block: suspend () -> T): T
}

class RoomTransactionRunner(
    private val database: GoodBeforeDatabase
) : TransactionRunner {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction(block)
}
