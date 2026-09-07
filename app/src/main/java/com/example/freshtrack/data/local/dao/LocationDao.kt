package com.example.freshtrack.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.freshtrack.data.local.entities.LocationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationDao {

    @Query(
        """
        SELECT * FROM locations
        WHERE kitchenId = :kitchenId AND archivedAt IS NULL
        ORDER BY sortOrder ASC
        """
    )
    fun getLocations(kitchenId: String): Flow<List<LocationEntity>>

    @Query(
        """
        SELECT * FROM locations
        WHERE kitchenId = :kitchenId AND archivedAt IS NULL
        ORDER BY sortOrder ASC
        """
    )
    suspend fun getLocationsOnce(kitchenId: String): List<LocationEntity>

    /**
     * Includes archived locations, so an item that still points at one can show
     * a name instead of nothing.
     */
    @Query("SELECT * FROM locations WHERE id = :locationId")
    suspend fun getLocationById(locationId: String): LocationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(locations: List<LocationEntity>)

    @Update
    suspend fun update(location: LocationEntity)

    /**
     * Archive rather than delete. Items keep a resolvable reference, and the
     * place stops being offered for new ones.
     */
    @Query("UPDATE locations SET archivedAt = :at, updatedAt = :at WHERE id = :locationId")
    suspend fun archive(locationId: String, at: Long)

    @Query("UPDATE locations SET kitchenId = :kitchenId WHERE kitchenId = :localKitchenId")
    suspend fun claimLocalLocations(localKitchenId: String, kitchenId: String)

    @Query("DELETE FROM locations WHERE kitchenId = :kitchenId")
    suspend fun deleteAllForKitchen(kitchenId: String)
}
