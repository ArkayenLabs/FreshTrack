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
 *
 * Ordered by how often the thing goes off, roughly: the short-dated aisles
 * first, the store cupboard last. Meat & Fish and Ready Meals are the two
 * aisles where nearly everything carries a use-by, which is the whole reason
 * this app exists, so they sit near the front. "Store Cupboard" is the name
 * because "Pantry" is a location, and a tin of tuna kept in the fridge should
 * not have to be in two places at once.
 *
 * These names are stored on every item, so a change here is a migration, not
 * an edit — see `GoodBeforeDatabase.MIGRATION_2_3` for the last one.
 */
object DefaultCategories {
    val FRESH_PRODUCE = CategoryEntity("Fresh Produce", "#4CAF50", "eco", 0)
    val DAIRY = CategoryEntity("Dairy & Eggs", "#2196F3", "water_drop", 1)
    val MEAT_FISH = CategoryEntity("Meat & Fish", "#E53935", "set_meal", 2)
    val READY_MEALS = CategoryEntity("Ready Meals", "#8E24AA", "lunch_dining", 3)
    val BAKERY = CategoryEntity("Bakery", "#FF9800", "bakery_dining", 4)
    val BEVERAGES = CategoryEntity("Beverages", "#00BCD4", "local_drink", 5)
    val STORE_CUPBOARD = CategoryEntity("Store Cupboard", "#795548", "kitchen", 6)
    val LEFTOVERS = CategoryEntity("Leftovers", "#FF5722", "takeout_dining", 7)
    val OTHER = CategoryEntity("Other", "#9E9E9E", "category", 8)

    fun getAll() = listOf(
        FRESH_PRODUCE, DAIRY, MEAT_FISH, READY_MEALS, BAKERY,
        BEVERAGES, STORE_CUPBOARD, LEFTOVERS, OTHER
    )
}
