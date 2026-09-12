package com.example.freshtrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.freshtrack.data.local.entities.ItemEntity
import com.example.freshtrack.data.local.entities.ItemState
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Reads and writes for the items table.
 *
 * Every user-facing query filters on `kitchenId` and `isDeleted = 0`.
 *
 * `kitchenId` is the access key rather than the creator's uid, because a shared
 * household kitchen is visible to several accounts. On one device, two accounts
 * resolve to different kitchens, and that is what keeps their food separate.
 *
 * The queries under "Sync" deliberately skip the tombstone filter: a deletion
 * sync cannot see is a deletion that can never reach another device.
 */
@Dao
interface ItemDao {

    // ─── Reads ───────────────────────────────────────────────────────────────

    @Query(
        """
        SELECT * FROM items
        WHERE kitchenId = :kitchenId AND isDeleted = 0 AND state = 'ACTIVE'
        ORDER BY expiryDate ASC
        """
    )
    fun getActiveItems(kitchenId: String): Flow<List<ItemEntity>>

    @Query(
        """
        SELECT * FROM items
        WHERE kitchenId = :kitchenId AND isDeleted = 0 AND state = 'ACTIVE'
        AND category = :category
        ORDER BY expiryDate ASC
        """
    )
    fun getActiveItemsByCategory(kitchenId: String, category: String): Flow<List<ItemEntity>>

    @Query(
        """
        SELECT * FROM items
        WHERE kitchenId = :kitchenId AND isDeleted = 0 AND state = 'ACTIVE'
        AND locationId = :locationId
        ORDER BY expiryDate ASC
        """
    )
    fun getActiveItemsByLocation(kitchenId: String, locationId: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :itemId AND kitchenId = :kitchenId AND isDeleted = 0")
    fun getItemById(kitchenId: String, itemId: String): Flow<ItemEntity?>

    @Query("SELECT * FROM items WHERE id = :itemId AND kitchenId = :kitchenId AND isDeleted = 0")
    suspend fun getItemByIdOnce(kitchenId: String, itemId: String): ItemEntity?

    @Query(
        """
        SELECT * FROM items
        WHERE kitchenId = :kitchenId AND isDeleted = 0 AND state = 'ACTIVE'
        AND barcode = :barcode
        LIMIT 1
        """
    )
    suspend fun getItemByBarcode(kitchenId: String, barcode: String): ItemEntity?

    /**
     * Items due on or before [through], including ones already past their date.
     *
     * Already-expired items are included on purpose. An earlier version bounded
     * this below by "today" as well, which meant anything that had already gone
     * past its date dropped out of every alert — silently excluding exactly the
     * items the user most needed telling about.
     *
     * [now] excludes anything the user has snoozed past.
     */
    @Query(
        """
        SELECT * FROM items
        WHERE kitchenId = :kitchenId AND isDeleted = 0 AND state = 'ACTIVE'
        AND expiryDate <= :through
        AND notificationEnabled = 1
        AND (snoozedUntil IS NULL OR snoozedUntil <= :now)
        ORDER BY expiryDate ASC
        """
    )
    suspend fun getItemsDueThrough(
        kitchenId: String,
        through: LocalDate,
        now: Long
    ): List<ItemEntity>

    @Query(
        """
        SELECT * FROM items
        WHERE kitchenId = :kitchenId AND isDeleted = 0 AND state = 'ACTIVE'
        AND expiryDate < :today
        ORDER BY expiryDate DESC
        """
    )
    fun getExpiredItems(kitchenId: String, today: LocalDate): Flow<List<ItemEntity>>

    @Query(
        """
        SELECT * FROM items
        WHERE kitchenId = :kitchenId AND isDeleted = 0 AND state = :state
        ORDER BY resolvedAt DESC
        """
    )
    fun getItemsInState(kitchenId: String, state: ItemState): Flow<List<ItemEntity>>

    @Query(
        "SELECT COUNT(*) FROM items WHERE kitchenId = :kitchenId AND isDeleted = 0 AND state = 'ACTIVE'"
    )
    fun getActiveItemCount(kitchenId: String): Flow<Int>

    /**
     * An existing active item that looks like the same physical thing: same
     * name, expiring the same day.
     *
     * The date is part of the key on purpose. Buying milk twice in a week is
     * two genuine batches, not a duplicate; the same name *and* the same date
     * is what indicates one thing entered twice. Name matching is
     * case-insensitive because the column collates NOCASE.
     */
    @Query(
        """
        SELECT * FROM items
        WHERE kitchenId = :kitchenId AND isDeleted = 0 AND state = 'ACTIVE'
        AND name = :name AND expiryDate = :expiryDate
        LIMIT 1
        """
    )
    suspend fun findActiveDuplicate(
        kitchenId: String,
        name: String,
        expiryDate: LocalDate
    ): ItemEntity?

    // ─── Writes ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ItemEntity>)

    @Update
    suspend fun update(item: ItemEntity)

    /**
     * Soft delete. The row stays as a tombstone so the deletion can be replayed
     * onto another device.
     */
    @Query(
        """
        UPDATE items SET isDeleted = 1, deletedAt = :at, updatedAt = :at, lastEditedBy = :actorUid
        WHERE id = :itemId AND kitchenId = :kitchenId
        """
    )
    suspend fun softDelete(kitchenId: String, itemId: String, actorUid: String, at: Long)

    @Query(
        """
        UPDATE items SET state = :state, resolvedAt = :at, updatedAt = :at, lastEditedBy = :actorUid
        WHERE id = :itemId AND kitchenId = :kitchenId
        """
    )
    suspend fun setState(
        kitchenId: String,
        itemId: String,
        state: ItemState,
        actorUid: String,
        at: Long
    )

    @Query(
        """
        UPDATE items SET quantity = :quantity, updatedAt = :at, lastEditedBy = :actorUid
        WHERE id = :itemId AND kitchenId = :kitchenId
        """
    )
    suspend fun setQuantity(
        kitchenId: String,
        itemId: String,
        quantity: Int,
        actorUid: String,
        at: Long
    )

    @Query(
        """
        UPDATE items SET snoozedUntil = :until, updatedAt = :at, lastEditedBy = :actorUid
        WHERE id = :itemId AND kitchenId = :kitchenId
        """
    )
    suspend fun setSnoozedUntil(
        kitchenId: String,
        itemId: String,
        until: Long?,
        actorUid: String,
        at: Long
    )

    /** Soft-deletes everything already used or discarded. Clears history. */
    @Query(
        """
        UPDATE items SET isDeleted = 1, deletedAt = :at, updatedAt = :at
        WHERE kitchenId = :kitchenId AND state != 'ACTIVE'
        """
    )
    suspend fun softDeleteResolved(kitchenId: String, at: Long)

    // ─── Account handover ────────────────────────────────────────────────────

    /** Adopts rows created before sign-in into the account's own kitchen. */
    @Query(
        """
        UPDATE items
        SET kitchenId = :kitchenId, createdBy = :uid, lastEditedBy = :uid, updatedAt = :at
        WHERE kitchenId = :localKitchenId
        """
    )
    suspend fun claimLocalItems(
        localKitchenId: String,
        kitchenId: String,
        uid: String,
        at: Long
    )

    @Query("SELECT COUNT(*) FROM items WHERE kitchenId = :localKitchenId AND isDeleted = 0")
    suspend fun countLocalItems(localKitchenId: String): Int

    /**
     * Hard delete, used only when an account is being erased. Tombstones are
     * pointless there: no account remains for the deletion to reach.
     */
    @Query("DELETE FROM items WHERE kitchenId = :kitchenId")
    suspend fun deleteAllForKitchen(kitchenId: String)

    // ─── Sync ────────────────────────────────────────────────────────────────
    // These do not filter isDeleted, so tombstones can be pushed.

    @Query("SELECT * FROM items WHERE id = :itemId")
    suspend fun getByIdIncludingDeleted(itemId: String): ItemEntity?

    @Query("SELECT * FROM items WHERE id IN (:itemIds)")
    suspend fun getByIdsIncludingDeleted(itemIds: List<String>): List<ItemEntity>

    /** Everything in the kitchen, tombstones included, for a first backup. */
    @Query("SELECT * FROM items WHERE kitchenId = :kitchenId")
    suspend fun getAllIncludingDeleted(kitchenId: String): List<ItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFromRemote(items: List<ItemEntity>)
}
