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

    /**
     * The whole ledger for a kitchen, in a fixed order, for a first backup.
     *
     * The order is what makes the upload resumable: "the first N are done" is
     * only meaningful if the same N come first every time.
     */
    @Query("SELECT * FROM item_events WHERE kitchenId = :kitchenId ORDER BY occurredAt ASC, id ASC")
    suspend fun getAllForKitchen(kitchenId: String): List<ItemEventEntity>

    // ─── Impact, derived from the ledger ─────────────────────────────────────

    /**
     * Units resolved one way or the other, net of anything undone.
     *
     * Sums quantity rather than counting rows, so using two of three units is
     * two, not one. Subtracts reversals, because an undone use is food the user
     * explicitly told us they had not eaten after all — leaving it in would make
     * impact disagree with the history it is derived from.
     *
     * COALESCE twice because SUM over no rows is null, not zero.
     */
    @Query(
        """
        SELECT COALESCE(
            (SELECT SUM(quantity) FROM item_events
             WHERE kitchenId = :kitchenId AND type = :type), 0
        ) - COALESCE(
            (SELECT SUM(quantity) FROM item_events
             WHERE kitchenId = :kitchenId
             AND type = 'ITEM_RESTORED'
             AND reversesEventType = :type), 0
        )
        """
    )
    fun sumQuantityForType(kitchenId: String, type: ItemEventType): Flow<Int>

    /**
     * When the user last actually threw something away.
     *
     * Excludes discards that were undone. Without that, tapping "Bin" by
     * mistake would reset a waste-free streak that was never broken, and the
     * correction would not bring it back.
     */
    @Query(
        """
        SELECT MAX(e.occurredAt) FROM item_events e
        WHERE e.kitchenId = :kitchenId
        AND e.type = 'QUANTITY_DISCARDED'
        AND NOT EXISTS (
            SELECT 1 FROM item_events r WHERE r.reversesEventId = e.id
        )
        """
    )
    fun getLastDiscardAt(kitchenId: String): Flow<Long?>

    /**
     * The most recent resolution of this item that has not already been undone.
     *
     * Undo reverses one step at a time, so an already-reversed event must not be
     * offered again — otherwise repeated taps would keep crediting quantity back
     * that was never taken.
     *
     * Ordered by rowid as well as time, because two resolutions of the same item
     * can land in the same millisecond — a fast double action, or a replayed
     * batch — and "the latest" then has no answer. rowid is insertion order,
     * which is exactly the tiebreak wanted.
     */
    @Query(
        """
        SELECT * FROM item_events e
        WHERE e.itemId = :itemId
        AND e.type IN ('QUANTITY_USED', 'QUANTITY_DISCARDED')
        AND NOT EXISTS (
            SELECT 1 FROM item_events r WHERE r.reversesEventId = e.id
        )
        ORDER BY e.occurredAt DESC, e.rowid DESC
        LIMIT 1
        """
    )
    suspend fun findLatestUnreversedResolution(itemId: String): ItemEventEntity?

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

    /**
     * Adopts events recorded before sign-in into the account's kitchen.
     *
     * The actor is rewritten too. Every event in the local kitchen was recorded
     * by the guest sentinel, and by signing in the person has said the guest
     * was them. Leaving "guest" would be refused by the rules on push, which
     * require an event to name its actor as the signed-in user.
     */
    @Query(
        """
        UPDATE item_events
        SET kitchenId = :kitchenId, actorUid = :uid
        WHERE kitchenId = :localKitchenId
        """
    )
    suspend fun claimLocalEvents(localKitchenId: String, kitchenId: String, uid: String)

    @Query("DELETE FROM item_events WHERE kitchenId = :kitchenId")
    suspend fun deleteAllForKitchen(kitchenId: String)
}
