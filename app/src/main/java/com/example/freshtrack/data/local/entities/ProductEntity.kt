package com.example.freshtrack.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Product entity representing items tracked in the app
 * Stores all information about expirable products
 */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val barcode: String? = null,
    val category: String,
    val expiryDate: Long, // Unix timestamp in milliseconds
    val addedDate: Long = System.currentTimeMillis(),
    val quantity: Int = 1,
    val originalQuantity: Int = quantity,
    val notes: String? = null,
    val imageUri: String? = null,
    val notificationEnabled: Boolean = true,
    val isConsumed: Boolean = false,
    val isDiscarded: Boolean = false
)

/**
 * Category entity for product categorization
 * Provides organization and visual grouping
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    val name: String,
    val colorHex: String, // Hex color code (e.g., "#4CAF50")
    val icon: String, // Material icon name
    val sortOrder: Int = 0
)

/**
 * Predefined categories for initial setup
 */
object DefaultCategories {
    val FOOD = CategoryEntity(
        name = "Food",
        colorHex = "#4CAF50", // Green
        icon = "restaurant",
        sortOrder = 0
    )

    val MEDICINE = CategoryEntity(
        name = "Medicine",
        colorHex = "#F44336", // Red
        icon = "medication",
        sortOrder = 1
    )

    val COSMETICS = CategoryEntity(
        name = "Cosmetics",
        colorHex = "#E91E63", // Pink
        icon = "face",
        sortOrder = 2
    )

    val BEVERAGES = CategoryEntity(
        name = "Beverages",
        colorHex = "#2196F3", // Blue
        icon = "local_drink",
        sortOrder = 3
    )

    val OTHER = CategoryEntity(
        name = "Other",
        colorHex = "#9E9E9E", // Grey
        icon = "category",
        sortOrder = 4
    )

    fun getAll() = listOf(FOOD, MEDICINE, COSMETICS, BEVERAGES, OTHER)
}