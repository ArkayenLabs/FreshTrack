package com.example.freshtrack.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The kind of storage, which is what determines how long food lasts there.
 *
 * Kept as a type separate from the user's own name for the place, so someone
 * can call it "garage freezer" without the shelf-life rules losing track of the
 * fact that it is a freezer.
 */
enum class LocationType {
    FRIDGE,
    FREEZER,
    PANTRY,
    COUNTER,
    OTHER
}

/**
 * Somewhere food is physically kept.
 *
 * This is not a category. "Dairy" says what a thing is; "Fridge" says where it
 * is. The previous schema only had the first, which meant the app could not
 * answer "what is in the freezer" or apply storage-dependent shelf life.
 */
@Entity(
    tableName = "locations",
    indices = [Index(value = ["kitchenId"])]
)
data class LocationEntity(
    @PrimaryKey
    val id: String,

    val kitchenId: String = LOCAL_KITCHEN_ID,

    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val name: String,

    val type: LocationType,

    val sortOrder: Int = 0,

    /**
     * Archived rather than deleted, so items that still reference this location
     * keep a readable name instead of pointing at nothing.
     */
    val archivedAt: Long? = null,

    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * The locations a new kitchen starts with.
 *
 * Ids are fixed strings rather than random UUIDs so that the same physical
 * place resolves to the same row on every device without needing to sync first.
 */
object DefaultLocations {
    val FRIDGE = LocationEntity(id = "loc-fridge", name = "Fridge", type = LocationType.FRIDGE, sortOrder = 0)
    val FREEZER = LocationEntity(id = "loc-freezer", name = "Freezer", type = LocationType.FREEZER, sortOrder = 1)
    val PANTRY = LocationEntity(id = "loc-pantry", name = "Pantry", type = LocationType.PANTRY, sortOrder = 2)
    val COUNTER = LocationEntity(id = "loc-counter", name = "Counter", type = LocationType.COUNTER, sortOrder = 3)

    fun getAll() = listOf(FRIDGE, FREEZER, PANTRY, COUNTER)
}
