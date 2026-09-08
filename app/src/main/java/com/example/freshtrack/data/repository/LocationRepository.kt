package com.example.freshtrack.data.repository

import com.example.freshtrack.data.local.dao.LocationDao
import com.example.freshtrack.data.local.entities.LocationEntity
import com.example.freshtrack.data.local.entities.LocationType
import com.example.freshtrack.data.session.KitchenSession
import com.example.freshtrack.util.IdGenerator
import kotlinx.coroutines.flow.Flow

/**
 * Where food is kept.
 *
 * Separate from categories throughout: a thing has one category and sits in one
 * place, and those are independent facts.
 */
interface LocationRepository {
    fun observeLocations(): Flow<List<LocationEntity>>
    suspend fun getLocations(): List<LocationEntity>
    suspend fun getLocation(locationId: String): LocationEntity?
    suspend fun add(name: String, type: LocationType): String
    suspend fun rename(locationId: String, name: String)

    /** Archives rather than deletes, so items still resolve to a readable name. */
    suspend fun archive(locationId: String)
}

class LocationRepositoryImpl(
    private val locationDao: LocationDao,
    private val session: KitchenSession,
    private val ids: IdGenerator = IdGenerator.Uuid
) : LocationRepository {

    private fun kitchen(): String = session.activeKitchenId()

    override fun observeLocations(): Flow<List<LocationEntity>> =
        locationDao.getLocations(kitchen())

    override suspend fun getLocations(): List<LocationEntity> =
        locationDao.getLocationsOnce(kitchen())

    override suspend fun getLocation(locationId: String): LocationEntity? =
        locationDao.getLocationById(locationId)

    override suspend fun add(name: String, type: LocationType): String {
        val existing = locationDao.getLocationsOnce(kitchen())
        val location = LocationEntity(
            id = ids.newId(),
            kitchenId = kitchen(),
            name = name.trim(),
            type = type,
            sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        )
        locationDao.insert(location)
        return location.id
    }

    override suspend fun rename(locationId: String, name: String) {
        val existing = locationDao.getLocationById(locationId) ?: return
        locationDao.update(
            existing.copy(name = name.trim(), updatedAt = System.currentTimeMillis())
        )
    }

    override suspend fun archive(locationId: String) {
        locationDao.archive(locationId, System.currentTimeMillis())
    }
}
