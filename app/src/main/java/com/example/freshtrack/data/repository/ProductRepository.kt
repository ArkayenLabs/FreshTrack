package com.example.freshtrack.data.repository

import com.example.freshtrack.data.local.dao.CategoryDao
import com.example.freshtrack.data.local.dao.ProductDao
import com.example.freshtrack.domain.model.*
import kotlinx.coroutines.flow.Flow
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
    private val productDao: ProductDao
) : ProductRepository {

    override fun getAllProducts(): Flow<List<Product>> {
        return productDao.getAllActiveProducts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getProductsByCategory(category: String): Flow<List<Product>> {
        return productDao.getProductsByCategory(category).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getProductById(productId: String): Flow<Product?> {
        return productDao.getProductById(productId).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun getProductByIdOnce(productId: String): Product? {
        return productDao.getProductByIdOnce(productId)?.toDomain()
    }

    override suspend fun getExpiringProducts(daysThreshold: Int): List<Product> {
        val currentTime = System.currentTimeMillis()
        val thresholdTime = currentTime + TimeUnit.DAYS.toMillis(daysThreshold.toLong())

        return productDao.getExpiringProducts(
            timestampThreshold = thresholdTime,
            currentTimestamp = currentTime
        ).map { it.toDomain() }
    }

    override fun getExpiredProducts(): Flow<List<Product>> {
        return productDao.getExpiredProducts().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun insertProduct(product: Product) {
        productDao.insertProduct(product.toEntity())
    }

    override suspend fun updateProduct(product: Product) {
        productDao.updateProduct(product.toEntity())
    }

    override suspend fun deleteProduct(productId: String) {
        productDao.deleteProductById(productId)
    }

    override suspend fun markAsConsumed(productId: String) {
        productDao.markAsConsumed(productId)
    }

    override suspend fun markAsDiscarded(productId: String) {
        productDao.markAsDiscarded(productId)
    }

    override suspend fun updateProductQuantity(productId: String, newQuantity: Int) {
        val product = productDao.getProductByIdOnce(productId)
        product?.let {
            productDao.updateProduct(it.copy(quantity = newQuantity))
        }
    }

    override fun getActiveProductCount(): Flow<Int> {
        return productDao.getActiveProductCount()
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