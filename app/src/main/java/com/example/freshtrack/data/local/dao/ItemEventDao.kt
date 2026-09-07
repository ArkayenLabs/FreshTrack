package com.example.freshtrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.freshtrack.data.local.entities.ItemEventEntity
import com.example.freshtrack.data.local.entities.ItemEventType
import kotlinx.coroutines.flow.Flow

/**
 * The append-only ledger.
 *
 * There is no update and no delete here, and that is the point. Impact figures
 * are read from these rows rather than counted off item states, so editing or
 * removing an item cannot retroactively change what the user is told they did.
 *
 * Inserts IGNORE on conflict rather than replacing. Combined with the unique
 * index on `operationId`, that makes appending idempotent: a retried push, a
 * replayed pull, or a notification action tapped twice records one event.
 */
@Dao
interface ItemEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun append(event: ItemEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun appendAll(events: List<ItemEventEntity>): List<Long>

    @Query("SELECT * FROM item_events WHERE itemId = :itemId ORDER BY occurredAt DESC")
    fun getEventsForItem(itemId: String): Flow<List<ItemEventEntity>>

    @Query(
        """
        SELECT * FROM item_events
        WHERE kitchenId = :kitchenId
        ORDER BY occurredAt DESC
        LIMIT :limit
        """
    )
    fun getRecentEvents(kitchenId: String, limit: Int): Flow<List<ItemEventEntity>>

    @Query("SELECT * FROM item_events WHERE operationId = :operationId LIMIT 1")
    suspend fun findByOperationId(operationId: String): ItemEventEntity?

    // ─── Impact, derived from the ledger ─────────────────────────────────────

    /**
     * Units resolved one way or the other.
     *
     * Sums quantity rather than counting rows, so using two of three units is
     * two, not one. COALESCE because SUM over no rows is null, not zero.
     */
    @Query(
        """
        SELECT COALESCE(SUM(quantity), 0) FROM item_events
        WHERE kitchenId = :kitchenId AND type = :type
        """
    )
    fun sumQuantityForType(kitchenId: String, type: ItemEventType): Flow<Int>

    @Query(
        """
        SELECT MAX(occurredAt) FROM item_events
        WHERE kitchenId = :kitchenId AND type = 'QUANTITY_DISCARDED'
        """
    )
    fun getLastDiscardAt(kitchenId: String): Flow<Long?>

    @Query("SELECT MIN(occurredAt) FROM item_events WHERE kitchenId = :kitchenId")
    fun getFirstEventAt(kitchenId: String): Flow<Long?>

    /**
     * Value of what was used, counting only prices that were observed rather
     * than inferred, so a money figure is never built out of guesses.
     */
    @Query(
        """
        SELECT COALESCE(SUM(i.priceMinorUnits * e.quantity), 0)
        FROM item_events e
        JOIN items i ON i.id = e.itemId
        WHERE e.kitchenId = :kitchenId
        AND e.type = :type
        AND i.priceMinorUnits IS NOT NULL
        AND i.priceSource IN ('USER', 'RECEIPT')
        """
    )
    fun sumVerifiedValueForType(kitchenId: String, type: ItemEventType): Flow<Long>

    // ─── Account handover and deletion ───────────────────────────────────────

    @Query("UPDATE item_events SET kitchenId = :kitchenId WHERE kitchenId = :localKitchenId")
    suspend fun claimLocalEvents(localKitchenId: String, kitchenId: String)

    @Query("DELETE FROM item_events WHERE kitchenId = :kitchenId")
    suspend fun deleteAllForKitchen(kitchenId: String)
}
