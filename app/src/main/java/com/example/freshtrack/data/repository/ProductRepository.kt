package com.example.freshtrack.data.repository

import com.example.freshtrack.data.local.dao.CategoryDao
import com.example.freshtrack.data.local.dao.ProductDao
import com.example.freshtrack.data.session.UserSession
import com.example.freshtrack.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

/**
 * Repository interface for Product operations
 * Defines contract for data access (Clean Architecture)
 */
interface ProductRepository {
    fun getAllProducts(): Flow<List<Product>>
    fun getProductsByCategory(category: String): Flow<List<Product>>
    fun getProductById(productId: String): Flow<Product?>
    suspend fun getProductByIdOnce(productId: String): Product?
    suspend fun getExpiringProducts(daysThreshold: Int): List<Product>
    fun getExpiredProducts(): Flow<List<Product>>
    suspend fun insertProduct(product: Product)
    suspend fun updateProduct(product: Product)
    suspend fun deleteProduct(productId: String)
    suspend fun markAsConsumed(productId: String)
    suspend fun markAsDiscarded(productId: String)
    suspend fun updateProductQuantity(productId: String, newQuantity: Int)
    fun getActiveProductCount(): Flow<Int>
    fun getConsumedProducts(): Flow<List<Product>>
    fun getDiscardedProducts(): Flow<List<Product>>
    fun getImpactStats(): Flow<ImpactStats>
    suspend fun deleteHistory()

    /**
     * Adopts any guest-owned rows for the signed-in account. Returns how many
     * were claimed. No-op when signed out.
     */
    suspend fun claimGuestData(): Int
}

/**
 * Repository interface for Category operations
 */
interface CategoryRepository {
    fun getAllCategories(): Flow<List<Category>>
    suspend fun getAllCategoriesOnce(): List<Category>
    suspend fun getCategoryByName(name: String): Category?
    suspend fun insertCategory(category: Category)
    suspend fun updateCategory(category: Category)
    suspend fun deleteCategory(name: String)
}

/**
 * Implementation of ProductRepository
 * Handles data operations and domain/entity mapping
 */
class ProductRepositoryImpl(
    private val productDao: ProductDao,
    private val session: UserSession
) : ProductRepository {

    /** Resolved per call so a sign-in or sign-out takes effect immediately. */
    private fun uid(): String = session.currentUserId()

    override fun getAllProducts(): Flow<List<Product>> {
        return productDao.getAllActiveProducts(uid()).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getProductsByCategory(category: String): Flow<List<Product>> {
        return productDao.getProductsByCategory(uid(), category).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getProductById(productId: String): Flow<Product?> {
        return productDao.getProductById(uid(), productId).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun getProductByIdOnce(productId: String): Product? {
        return productDao.getProductByIdOnce(uid(), productId)?.toDomain()
    }

    override suspend fun getExpiringProducts(daysThreshold: Int): List<Product> {
        val currentTime = System.currentTimeMillis()
        val thresholdTime = currentTime + TimeUnit.DAYS.toMillis(daysThreshold.toLong())

        return productDao.getExpiringProducts(
            userId = uid(),
            timestampThreshold = thresholdTime,
            currentTimestamp = currentTime
        ).map { it.toDomain() }
    }

    override fun getExpiredProducts(): Flow<List<Product>> {
        return productDao.getExpiredProducts(uid()).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertProduct(product: Product) {
        val trimmedProduct = product.copy(
            name = product.name.trim(),
            category = product.category.trim()
        )
        // Ownership and last-write are stamped here rather than at the call site,
        // so no screen can create an unowned row.
        productDao.insertProduct(
            trimmedProduct.toEntity().copy(
                userId = uid(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun updateProduct(product: Product) {
        val trimmedProduct = product.copy(
            name = product.name.trim(),
            category = product.category.trim()
        )
        productDao.updateProduct(
            trimmedProduct.toEntity().copy(
                userId = uid(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    override suspend fun deleteProduct(productId: String) {
        productDao.softDeleteProductById(uid(), productId, System.currentTimeMillis())
    }

    override suspend fun markAsConsumed(productId: String) {
        productDao.markAsConsumed(uid(), productId, System.currentTimeMillis())
    }

    override suspend fun markAsDiscarded(productId: String) {
        productDao.markAsDiscarded(uid(), productId, System.currentTimeMillis())
    }

    override suspend fun updateProductQuantity(productId: String, newQuantity: Int) {
        val product = productDao.getProductByIdOnce(uid(), productId)
        product?.let {
            productDao.updateProduct(
                it.copy(quantity = newQuantity, updatedAt = System.currentTimeMillis())
            )
        }
    }

    override fun getActiveProductCount(): Flow<Int> {
        return productDao.getActiveProductCount(uid())
    }

    override fun getConsumedProducts(): Flow<List<Product>> {
        return productDao.getConsumedProducts(uid()).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getDiscardedProducts(): Flow<List<Product>> {
        return productDao.getDiscardedProducts(uid()).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getImpactStats(): Flow<ImpactStats> {
        val userId = uid()
        return combine(
            productDao.getConsumedCount(userId),
            productDao.getDiscardedCount(userId),
            productDao.getLastDiscardedAt(userId),
            productDao.getFirstActivityAt(userId)
        ) { saved, wasted, lastDiscardedAt, firstActivityAt ->
            val now = System.currentTimeMillis()

            // Days since the last discard. With no discards on record we count
            // from the user's first activity instead, so a user who has never
            // wasted anything still sees a growing number.
            val streakOrigin = lastDiscardedAt ?: firstActivityAt
            val wasteFreeDays = streakOrigin?.let { calendarDaysBetween(it, now) } ?: 0

            ImpactStats(
                itemsSaved = saved,
                itemsWasted = wasted,
                wasteFreeDays = wasteFreeDays,
                hasHistory = saved + wasted > 0
            )
        }
    }

    override suspend fun deleteHistory() {
        productDao.deleteHistory(uid(), System.currentTimeMillis())
    }

    override suspend fun claimGuestData(): Int {
        if (!session.isSignedIn()) return 0
        val pending = productDao.countGuestProducts()
        if (pending > 0) {
            productDao.claimGuestProducts(uid(), System.currentTimeMillis())
        }
        return pending
    }
}

/**
 * Implementation of CategoryRepository
 */
class CategoryRepositoryImpl(
    private val categoryDao: CategoryDao
) : CategoryRepository {

    override fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getAllCategoriesOnce(): List<Category> {
        return categoryDao.getAllCategoriesOnce().map { it.toDomain() }
    }

    override suspend fun getCategoryByName(name: String): Category? {
        return categoryDao.getCategoryByName(name)?.toDomain()
    }

    override suspend fun insertCategory(category: Category) {
        categoryDao.insertCategory(category.toEntity())
    }

    override suspend fun updateCategory(category: Category) {
        categoryDao.updateCategory(category.toEntity())
    }

    override suspend fun deleteCategory(name: String) {
        categoryDao.deleteCategoryByName(name)
    }
}