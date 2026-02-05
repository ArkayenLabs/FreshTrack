package com.example.freshtrack.domain.model

import com.example.freshtrack.data.local.entities.CategoryEntity
import com.example.freshtrack.data.local.entities.ProductEntity
import java.util.concurrent.TimeUnit

/**
 * Domain model for Product
 * Clean architecture: separates data layer from presentation
 */
data class Product(
    val id: String,
    val name: String,
    val barcode: String?,
    val category: String,
    val expiryDate: Long,
    val addedDate: Long,
    val quantity: Int,
    val notes: String?,
    val imageUri: String?,
    val notificationEnabled: Boolean,
    val isConsumed: Boolean,
    val isDiscarded: Boolean
) {
    /**
     * Calculate days until expiry based on calendar dates
     * @return Days remaining (negative if expired)
     */
    fun daysUntilExpiry(): Long {
        val calendar = java.util.Calendar.getInstance()
        
        // Get today at midnight
        calendar.timeInMillis = System.currentTimeMillis()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val todayMidnight = calendar.timeInMillis
        
        // Get expiry date at midnight
        calendar.timeInMillis = expiryDate
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val expiryMidnight = calendar.timeInMillis
        
        return TimeUnit.MILLISECONDS.toDays(expiryMidnight - todayMidnight)
    }

    /**
     * Check if product is expired (based on calendar date)
     */
    fun isExpired(): Boolean = daysUntilExpiry() < 0

    /**
     * Get urgency level for color coding
     */
    fun getUrgency(): ExpiryUrgency {
        val days = daysUntilExpiry()
        return when {
            days < 0 -> ExpiryUrgency.EXPIRED
            days <= 2 -> ExpiryUrgency.CRITICAL
            days <= 7 -> ExpiryUrgency.WARNING
            else -> ExpiryUrgency.SAFE
        }
    }
}

/**
 * Domain model for Category
 */
data class Category(
    val name: String,
    val colorHex: String,
    val icon: String,
    val sortOrder: Int
)

/**
 * Expiry urgency levels for UI representation
 */
enum class ExpiryUrgency {
    SAFE,       // > 7 days (Green)
    WARNING,    // 3-7 days (Yellow)
    CRITICAL,   // 1-2 days (Orange)
    EXPIRED     // < 0 days (Red)
}

/**
 * Filter options for product list
 */
enum class ProductFilter {
    ALL,
    EXPIRING_SOON,
    EXPIRED,
    BY_CATEGORY
}

/**
 * Sort options for product list
 */
enum class ProductSort {
    EXPIRY_DATE_ASC,
    EXPIRY_DATE_DESC,
    NAME_ASC,
    NAME_DESC,
    ADDED_DATE_DESC
}

// Extension functions for mapping between layers

/**
 * Convert ProductEntity (data layer) to Product (domain layer)
 */
fun ProductEntity.toDomain(): Product {
    return Product(
        id = id,
        name = name,
        barcode = barcode,
        category = category,
        expiryDate = expiryDate,
        addedDate = addedDate,
        quantity = quantity,
        notes = notes,
        imageUri = imageUri,
        notificationEnabled = notificationEnabled,
        isConsumed = isConsumed,
        isDiscarded = isDiscarded
    )
}

/**
 * Convert Product (domain layer) to ProductEntity (data layer)
 */
fun Product.toEntity(): ProductEntity {
    return ProductEntity(
        id = id,
        name = name,
        barcode = barcode,
        category = category,
        expiryDate = expiryDate,
        addedDate = addedDate,
        quantity = quantity,
        notes = notes,
        imageUri = imageUri,
        notificationEnabled = notificationEnabled,
        isConsumed = isConsumed,
        isDiscarded = isDiscarded
    )
}

/**
 * Convert CategoryEntity to Category domain model
 */
fun CategoryEntity.toDomain(): Category {
    return Category(
        name = name,
        colorHex = colorHex,
        icon = icon,
        sortOrder = sortOrder
    )
}

/**
 * Convert Category to CategoryEntity
 */
fun Category.toEntity(): CategoryEntity {
    return CategoryEntity(
        name = name,
        colorHex = colorHex,
        icon = icon,
        sortOrder = sortOrder
    )
}