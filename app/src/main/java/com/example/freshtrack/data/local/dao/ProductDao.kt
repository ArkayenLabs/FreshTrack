package com.example.freshtrack.data.local.dao

import androidx.room.*
import com.example.freshtrack.data.local.entities.CategoryEntity
import com.example.freshtrack.data.local.entities.ProductEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Product operations
 * Provides reactive queries using Flow for automatic UI updates
 *
 * Every user-facing query filters on `userId` and `isDeleted = 0`. The owner
 * filter is what keeps two accounts on one device from seeing each other's
 * items; the tombstone filter hides soft-deleted rows that are kept so the
 * delete can propagate once sync exists.
 */
@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE userId = :userId AND isDeleted = 0 AND isConsumed = 0 AND isDiscarded = 0 ORDER BY expiryDate ASC")
    fun getAllActiveProducts(userId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE userId = :userId AND isDeleted = 0 AND category = :category AND isConsumed = 0 AND isDiscarded = 0 ORDER BY expiryDate ASC")
    fun getProductsByCategory(userId: String, category: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :productId AND userId = :userId AND isDeleted = 0")
    fun getProductById(userId: String, productId: String): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE id = :productId AND userId = :userId AND isDeleted = 0")
    suspend fun getProductByIdOnce(userId: String, productId: String): ProductEntity?

    /**
     * Get products expiring within specified days
     * @param timestampThreshold Unix timestamp threshold
     */
    @Query("""
        SELECT * FROM products
        WHERE userId = :userId
        AND isDeleted = 0
        AND expiryDate <= :timestampThreshold
        AND expiryDate >= :currentTimestamp
        AND isConsumed = 0
        AND isDiscarded = 0
        AND notificationEnabled = 1
        ORDER BY expiryDate ASC
    """)
    suspend fun getExpiringProducts(
        userId: String,
        timestampThreshold: Long,
        currentTimestamp: Long = System.currentTimeMillis()
    ): List<ProductEntity>

    @Query("""
        SELECT * FROM products
        WHERE userId = :userId
        AND isDeleted = 0
        AND expiryDate < :currentTimestamp
        AND isConsumed = 0
        AND isDiscarded = 0
        ORDER BY expiryDate DESC
    """)
    fun getExpiredProducts(
        userId: String,
        currentTimestamp: Long = System.currentTimeMillis()
    ): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE userId = :userId AND isDeleted = 0 AND barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(userId: String, barcode: String): ProductEntity?

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
    @Query("UPDATE products SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE id = :productId AND userId = :userId")
    suspend fun softDeleteProductById(userId: String, productId: String, deletedAt: Long)

    @Query("UPDATE products SET isConsumed = 1, resolvedDate = :resolvedAt, updatedAt = :resolvedAt WHERE id = :productId AND userId = :userId")
    suspend fun markAsConsumed(userId: String, productId: String, resolvedAt: Long)

    @Query("UPDATE products SET isDiscarded = 1, resolvedDate = :resolvedAt, updatedAt = :resolvedAt WHERE id = :productId AND userId = :userId")
    suspend fun markAsDiscarded(userId: String, productId: String, resolvedAt: Long)

    // ─── Impact Dashboard aggregates ──────────────────────────────────────────
    // All derived from Room so every call site counts and nothing can drift.

    @Query("SELECT COUNT(*) FROM products WHERE userId = :userId AND isDeleted = 0 AND isConsumed = 1")
    fun getConsumedCount(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE userId = :userId AND isDeleted = 0 AND isDiscarded = 1")
    fun getDiscardedCount(userId: String): Flow<Int>

    /**
     * Timestamp of the most recent discard, or null if the user has never
     * discarded anything. Drives the waste-free day count.
     */
    @Query("SELECT MAX(resolvedDate) FROM products WHERE userId = :userId AND isDeleted = 0 AND isDiscarded = 1")
    fun getLastDiscardedAt(userId: String): Flow<Long?>

    /**
     * Earliest activity in the account, used as the streak origin when the user
     * has never discarded anything.
     */
    @Query("SELECT MIN(addedDate) FROM products WHERE userId = :userId AND isDeleted = 0")
    fun getFirstActivityAt(userId: String): Flow<Long?>

    @Query("SELECT COUNT(*) FROM products WHERE userId = :userId AND isDeleted = 0 AND isConsumed = 0 AND isDiscarded = 0")
    fun getActiveProductCount(userId: String): Flow<Int>

    @Query("SELECT * FROM products WHERE userId = :userId AND isDeleted = 0 AND isConsumed = 1 ORDER BY addedDate DESC")
    fun getConsumedProducts(userId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE userId = :userId AND isDeleted = 0 AND isDiscarded = 1 ORDER BY addedDate DESC")
    fun getDiscardedProducts(userId: String): Flow<List<ProductEntity>>

    @Query("UPDATE products SET isDeleted = 1, deletedAt = :deletedAt, updatedAt = :deletedAt WHERE userId = :userId AND (isConsumed = 1 OR isDiscarded = 1)")
    suspend fun deleteHistory(userId: String, deletedAt: Long)

    // ─── Account handover ─────────────────────────────────────────────────────

    /**
     * Adopts rows left behind by guest use — and by the 5→6 migration, which
     * backfills all pre-account rows to 'guest' — for the signed-in account.
     */
    @Query("UPDATE products SET userId = :userId, updatedAt = :updatedAt WHERE userId = 'guest'")
    suspend fun claimGuestProducts(userId: String, updatedAt: Long)

    @Query("SELECT COUNT(*) FROM products WHERE userId = 'guest' AND isDeleted = 0")
    suspend fun countGuestProducts(): Int
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
