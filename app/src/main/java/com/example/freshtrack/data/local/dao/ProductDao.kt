package com.example.freshtrack.data.local.dao

import androidx.room.*
import com.example.freshtrack.data.local.entities.CategoryEntity
import com.example.freshtrack.data.local.entities.ProductEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Product operations
 * Provides reactive queries using Flow for automatic UI updates
 */
@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE isConsumed = 0 AND isDiscarded = 0 ORDER BY expiryDate ASC")
    fun getAllActiveProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE category = :category AND isConsumed = 0 AND isDiscarded = 0 ORDER BY expiryDate ASC")
    fun getProductsByCategory(category: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE id = :productId")
    fun getProductById(productId: String): Flow<ProductEntity?>

    @Query("SELECT * FROM products WHERE id = :productId")
    suspend fun getProductByIdOnce(productId: String): ProductEntity?

    /**
     * Get products expiring within specified days
     * @param timestampThreshold Unix timestamp threshold
     */
    @Query("""
        SELECT * FROM products 
        WHERE expiryDate <= :timestampThreshold 
        AND expiryDate >= :currentTimestamp
        AND isConsumed = 0 
        AND isDiscarded = 0
        AND notificationEnabled = 1
        ORDER BY expiryDate ASC
    """)
    suspend fun getExpiringProducts(
        timestampThreshold: Long,
        currentTimestamp: Long = System.currentTimeMillis()
    ): List<ProductEntity>

    @Query("""
        SELECT * FROM products 
        WHERE expiryDate < :currentTimestamp
        AND isConsumed = 0 
        AND isDiscarded = 0
        ORDER BY expiryDate DESC
    """)
    fun getExpiredProducts(
        currentTimestamp: Long = System.currentTimeMillis()
    ): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<ProductEntity>)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Delete
    suspend fun deleteProduct(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :productId")
    suspend fun deleteProductById(productId: String)

    @Query("UPDATE products SET isConsumed = 1 WHERE id = :productId")
    suspend fun markAsConsumed(productId: String)

    @Query("UPDATE products SET isDiscarded = 1 WHERE id = :productId")
    suspend fun markAsDiscarded(productId: String)

    @Query("SELECT COUNT(*) FROM products WHERE isConsumed = 0 AND isDiscarded = 0")
    fun getActiveProductCount(): Flow<Int>

    @Query("SELECT * FROM products WHERE isConsumed = 1 ORDER BY addedDate DESC")
    fun getConsumedProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isDiscarded = 1 ORDER BY addedDate DESC")
    fun getDiscardedProducts(): Flow<List<ProductEntity>>

    @Query("DELETE FROM products WHERE isConsumed = 1 OR isDiscarded = 1")
    suspend fun deleteHistory()

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()
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