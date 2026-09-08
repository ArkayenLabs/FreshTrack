package com.example.freshtrack.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** Creator id used for rows created before the user signed in. */
const val GUEST_USER_ID = "guest"

/**
 * What kind of food something is.
 *
 * Distinct from where it is kept — see [LocationEntity]. NOCASE on the primary
 * key so "dairy" and "Dairy" cannot become two categories.
 */
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey
    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val name: String,
    val colorHex: String,
    /** Material icon name, resolved by the UI. Never an emoji. */
    val icon: String,
    val sortOrder: Int = 0
)

/**
 * The food-only category set.
 *
 * Food only, deliberately: medicine and cosmetics were removed because expiry
 * advice for them is a different and riskier problem than "use this up".
 */
object DefaultCategories {
    val FRESH_PRODUCE = CategoryEntity("Fresh Produce", "#4CAF50", "eco", 0)
    val DAIRY = CategoryEntity("Dairy", "#2196F3", "water_drop", 1)
    val BAKERY = CategoryEntity("Bakery", "#FF9800", "bakery_dining", 2)
    val BEVERAGES = CategoryEntity("Beverages", "#00BCD4", "local_drink", 3)
    val PANTRY = CategoryEntity("Pantry", "#795548", "kitchen", 4)
    val LEFTOVERS = CategoryEntity("Leftovers", "#FF5722", "takeout_dining", 5)
    val OTHER = CategoryEntity("Other", "#9E9E9E", "category", 6)

    fun getAll() = listOf(FRESH_PRODUCE, DAIRY, BAKERY, BEVERAGES, PANTRY, LEFTOVERS, OTHER)
}
