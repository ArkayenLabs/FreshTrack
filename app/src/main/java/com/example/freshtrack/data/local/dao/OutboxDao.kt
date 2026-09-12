package com.example.freshtrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.freshtrack.data.local.entities.OutboxEntity
import kotlinx.coroutines.flow.Flow

/**
 * The queue of local changes that have not yet been accepted by the server.
 *
 * Entries leave only on acknowledgement, never on a timestamp, so an
 * interrupted push resumes instead of skipping.
 */
@Dao
interface OutboxDao {

    /**
     * IGNORE rather than REPLACE: an operation id is already unique and
     * re-enqueueing the same one must not reset its retry bookkeeping.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueue(operation: OutboxEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueAll(operations: List<OutboxEntity>)

    /** Oldest first, so the server sees changes in the order they were made. */
    @Query(
        """
        SELECT * FROM outbox
        WHERE kitchenId = :kitchenId
        ORDER BY clientSequence ASC
        LIMIT :limit
        """
    )
    suspend fun peek(kitchenId: String, limit: Int): List<OutboxEntity>

    @Query("SELECT COUNT(*) FROM outbox WHERE kitchenId = :kitchenId")
    fun getPendingCount(kitchenId: String): Flow<Int>

    @Query("SELECT MAX(clientSequence) FROM outbox")
    suspend fun getHighestSequence(): Long?

    /** Called only once the server has confirmed the operation. */
    @Query("DELETE FROM outbox WHERE operationId IN (:operationIds)")
    suspend fun acknowledge(operationIds: List<String>)

    @Query(
        """
        UPDATE outbox
        SET attemptCount = attemptCount + 1, lastAttemptAt = :at, lastError = :error
        WHERE operationId = :operationId
        """
    )
    suspend fun recordFailure(operationId: String, at: Long, error: String?)

    /**
     * Entries that have failed repeatedly. Surfaced rather than retried
     * forever, because a permanently rejected write is invisible otherwise.
     */
    @Query("SELECT * FROM outbox WHERE kitchenId = :kitchenId AND attemptCount >= :threshold")
    suspend fun getStuck(kitchenId: String, threshold: Int): List<OutboxEntity>

    /**
     * Adopts queued operations into the account's kitchen, actor included, for
     * the same reason as [ItemEventDao.claimLocalEvents]. The payload snapshot
     * is left as written; push takes kitchen and actor from these columns.
     */
    @Query(
        """
        UPDATE outbox
        SET kitchenId = :kitchenId, actorUid = :uid
        WHERE kitchenId = :localKitchenId
        """
    )
    suspend fun claimLocalOperations(localKitchenId: String, kitchenId: String, uid: String)

    @Query("DELETE FROM outbox WHERE kitchenId = :kitchenId")
    suspend fun deleteAllForKitchen(kitchenId: String)

    /**
     * Drops entries a first backup has made redundant, and only those. A
     * change queued while the backup was running has a higher sequence and
     * still needs to go up on its own.
     */
    @Query("DELETE FROM outbox WHERE kitchenId = :kitchenId AND clientSequence <= :sequence")
    suspend fun deleteUpTo(kitchenId: String, sequence: Long)

    @Query("DELETE FROM outbox")
    suspend fun clear()
}
