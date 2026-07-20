package com.example.freshtrack.data.local.dao

import androidx.room.*
import com.example.freshtrack.data.local.entities.CategoryEntity
import com.example.freshtrack.data.local.entities.ProductEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Product operations
 * Provides reactive queries using Flow for automatic UI updates
 *
 * Every user-facing query filters on `pantryId` and `isDeleted = 0`.
 *
 * `pantryId` is the access key, not `userId` — a shared household pantry is
 * visible to several accounts, so filtering by the viewer's uid would make a
 * downloaded shared item either look like their own or be invisible to them.
 * `userId` is kept for attribution only. On one device, two accounts resolve to
 * different pantries, which is what keeps their inventories separate.
 *
 * The tombstone filter hides soft-deleted rows. The queries under "Sync"
 * deliberately skip that filter, because a deletion that sync cannot see can
 * never be propagated.
 */
@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE pantryId = :pantryId AND isDeleted = 0 AND isConsumed = 0 AND isDiscarded = 0 ORDER BY expiryDate ASC")
    fun getAllActiveProducts(pantryId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE pantryId = :pantryId AND isDeleted = 0 AND category = :category AND isConsumed = 0 AND isDiscarded = 0 ORDER BY expiryDate ASC")
    fun getProductsByCategory(pantryId: String, category: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :productId AND pantryId = :pantryId AND isDeleted = 0")
    fun getProductById(pantryId: String, productId: String): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE id = :productId AND pantryId = :pantryId AND isDeleted = 0")
    suspend fun getProductByIdOnce(pantryId: String, productId: String): ProductEntity?

    /**
     * Get products expiring within specified days
     * @param timestampThreshold Unix timestamp threshold
     */
    @Query("""
        SELECT * FROM products
        WHERE pantryId = :pantryId
        AND isDeleted = 0
        AND expiryDate <= :timestampThreshold
        AND expiryDate >= :currentTimestamp
        AND isConsumed = 0
        AND isDiscarded = 0
        AND notificationEnabled = 1
        ORDER BY expiryDate ASC
    """)
    suspend fun getExpiringProducts(
        pantryId: String,
        timestampThreshold: Long,
        currentTimestamp: Long = System.currentTimeMillis()
    ): List<ProductEntity>

    @Query("""
        SELECT * FROM products
        WHERE pantryId = :pantryId
        AND isDeleted = 0
        AND expiryDate < :currentTimestamp
        AND isConsumed = 0
        AND isDiscarded = 0
        ORDER BY expiryDate DESC
    """)
    fun getExpiredProducts(
        pantryId: String,
        currentTimestamp: Long = System.currentTimeMillis()
    ): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE pantryId = :pantryId AND isDeleted = 0 AND barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(pantryId: String, barcode: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    /**
     * Soft delete. The row is kept as a tombstone so the deletion can be
     * replayed onto other devices once sync exists.
     */
    @Query("UPDATE products SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :productId AND pantryId = :pantryId")
    suspend fun softDeleteProductById(pantryId: String, productId: String, deletedAt: Long)

    @Query("UPDATE products SET isConsumed = 1, resolvedDate = :resolvedAt, updatedAt = :resolvedAt WHERE id = :productId AND pantryId = :pantryId")
    suspend fun markAsConsumed(pantryId: String, productId: String, resolvedAt: Long)

    @Query("UPDATE products SET isDiscarded = 1, resolvedDate = :resolvedAt, updatedAt = :resolvedAt WHERE id = :productId AND pantryId = :pantryId")
    suspend fun markAsDiscarded(pantryId: String, productId: String, resolvedAt: Long)

    // ─── Impact Dashboard aggregates ──────────────────────────────────────────
    // All derived from Room so every call site counts and nothing can drift.

    @Query("SELECT COUNT(*) FROM products WHERE pantryId = :pantryId AND isDeleted = 0 AND isConsumed = 1")
    fun getConsumedCount(pantryId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE pantryId = :pantryId AND isDeleted = 0 AND isDiscarded = 1")
    fun getDiscardedCount(pantryId: String): Flow<Int>

    /**
     * Timestamp of the most recent discard, or null if the user has never
     * discarded anything. Drives the waste-free day count.
     */
    @Query("SELECT MAX(resolvedDate) FROM products WHERE pantryId = :pantryId AND isDeleted = 0 AND isDiscarded = 1")
    fun getLastDiscardedAt(pantryId: String): Flow<Long?>

    /**
     * Earliest activity in the account, used as the streak origin when the user
     * has never discarded anything.
     */
    @Query("SELECT MIN(addedDate) FROM products WHERE pantryId = :pantryId AND isDeleted = 0")
    fun getFirstActivityAt(pantryId: String): Flow<Long?>

    @Query("SELECT COUNT(*) FROM products WHERE pantryId = :pantryId AND isDeleted = 0 AND isConsumed = 0 AND isDiscarded = 0")
    fun getActiveProductCount(pantryId: String): Flow<Int>

    @Query("SELECT * FROM products WHERE pantryId = :pantryId AND isDeleted = 0 AND isConsumed = 1 ORDER BY addedDate DESC")
    fun getConsumedProducts(pantryId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE pantryId = :pantryId AND isDeleted = 0 AND isDiscarded = 1 ORDER BY addedDate DESC")
    fun getDiscardedProducts(pantryId: String): Flow<List<ProductEntity>>

    @Query("UPDATE products SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE pantryId = :pantryId AND (isConsumed = 1 OR isDiscarded = 1)")
    suspend fun deleteHistory(pantryId: String, deletedAt: Long)

    // ─── Account handover ─────────────────────────────────────────────────────

    /**
     * Adopts rows left behind by guest use — and by the 5→6 migration, which
     * backfills all pre-account rows to 'guest' — for the signed-in account.
     */
    @Query("""
        UPDATE products
        SET pantryId = :pantryId, userId = :userId, updatedAt = :updatedAt
        WHERE pantryId = 'local'
    """)
    suspend fun claimGuestProducts(pantryId: String, userId: String, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM products WHERE pantryId = 'local' AND isDeleted = 0")
    suspend fun countGuestProducts(): Int

    /**
     * Hard delete, used only when an account is being deleted. Tombstones are
     * pointless here: there is no longer an account for a deletion to
     * propagate to.
     */
    @Query("DELETE FROM products WHERE pantryId = :pantryId")
    suspend fun deleteAllForPantry(pantryId: String)

    // ─── Duplicate detection ──────────────────────────────────────────────────

    /**
     * An active product with the same name expiring on the same day.
     *
     * Name matching is case-insensitive because the column is COLLATE NOCASE.
     * Expiry is part of the key on purpose: buying milk twice in a week is two
     * genuine items, not a duplicate. Same name *and* same date is what
     * indicates the same physical thing entered twice.
     */
    @Query("""
        SELECT * FROM products
        WHERE pantryId = :pantryId
        AND isDeleted = 0
        AND isConsumed = 0
        AND isDiscarded = 0
        AND name = :name
        AND expiryDate BETWEEN :dayStart AND :dayEnd
        LIMIT 1
    """)
    suspend fun findActiveDuplicate(
        pantryId: String,
        name: String,
        dayStart: Long,
        dayEnd: Long
    ): ProductEntity?

    // ─── Sync ─────────────────────────────────────────────────────────────────
    // These deliberately do NOT filter isDeleted. Sync has to see tombstones,
    // otherwise a deletion can never be pushed to another device.

    @Query("SELECT * FROM products WHERE pantryId = :pantryId AND updatedAt > :since")
    suspend fun getChangedSince(pantryId: String, since: Long): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :productId")
    suspend fun getByIdIncludingDeleted(productId: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFromRemote(products: List<ProductEntity>)
}

/**
 * Data Access Object for Category operations
 */
@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    fun getAllCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC")
    suspend fun getAllCategoriesOnce(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE name = :name")
    suspend fun getCategoryByName(name: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Update
    suspend fun updateCategory(category: CategoryEntity)

    @Delete
    suspend fun deleteCategory(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE name = :name")
    suspend fun deleteCategoryByName(name: String)
}
