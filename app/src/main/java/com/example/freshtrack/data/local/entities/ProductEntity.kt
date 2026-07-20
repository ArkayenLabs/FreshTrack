package com.example.freshtrack.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Product entity representing items tracked in the app
 * Stores all information about expirable products
 */
@Entity(
    tableName = "products",
    indices = [Index(value = ["userId"])]
)
data class ProductEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    /**
     * Firebase uid of the owner, or [GUEST_USER_ID] for rows created before the
     * user signed in. Every query filters on this so two accounts sharing a
     * device never see each other's items.
     */
    val userId: String = GUEST_USER_ID,
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val name: String,
    val barcode: String? = null,
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val category: String,
    val expiryDate: Long, // Unix timestamp in milliseconds
    val addedDate: Long = System.currentTimeMillis(),
    val quantity: Int = 1,
    val originalQuantity: Int = quantity,
    val notes: String? = null,
    val imageUri: String? = null,
    val notificationEnabled: Boolean = true,
    val isConsumed: Boolean = false,
    val isDiscarded: Boolean = false,
    // When the item was marked used or discarded. Null while still active.
    // Backfilled to addedDate for rows resolved before this column existed.
    val resolvedDate: Long? = null,
    /** Last local write. Drives last-write-wins when sync lands. */
    val updatedAt: Long = System.currentTimeMillis(),
    /**
     * Tombstone. A hard DELETE cannot propagate to other devices, so deletes are
     * soft and the row is filtered out of every user-facing query.
     */
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
)

/** Owner id used for rows created before the user signed in. */
const val GUEST_USER_ID = "guest"

/**
 * Category entity for product categorization
 * Provides organization and visual grouping
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val name: String,
    val colorHex: String, // Hex color code (e.g., "#4CAF50")
    val icon: String, // Material icon name
    val sortOrder: Int = 0
)

/**
 * Predefined food-focused categories for initial setup
 */
object DefaultCategories {
    val FRESH_PRODUCE = CategoryEntity(
        name = "Fresh Produce",
        colorHex = "#4CAF50", // Green
        icon = "eco",
        sortOrder = 0
    )

    val DAIRY = CategoryEntity(
        name = "Dairy",
        colorHex = "#2196F3", // Blue
        icon = "water_drop",
        sortOrder = 1
    )

    val BAKERY = CategoryEntity(
        name = "Bakery",
        colorHex = "#FF9800", // Amber
        icon = "bakery_dining",
        sortOrder = 2
    )

    val BEVERAGES = CategoryEntity(
        name = "Beverages",
        colorHex = "#00BCD4", // Cyan
        icon = "local_drink",
        sortOrder = 3
    )

    val PANTRY = CategoryEntity(
        name = "Pantry",
        colorHex = "#795548", // Brown
        icon = "kitchen",
        sortOrder = 4
    )

    val LEFTOVERS = CategoryEntity(
        name = "Leftovers",
        colorHex = "#FF5722", // Deep Orange
        icon = "takeout_dining",
        sortOrder = 5
    )

    val OTHER = CategoryEntity(
        name = "Other",
        colorHex = "#9E9E9E", // Grey
        icon = "category",
        sortOrder = 6
    )

    fun getAll() = listOf(FRESH_PRODUCE, DAIRY, BAKERY, BEVERAGES, PANTRY, LEFTOVERS, OTHER)
}