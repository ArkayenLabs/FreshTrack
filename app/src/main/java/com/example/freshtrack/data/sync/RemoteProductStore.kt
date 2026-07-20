package com.example.freshtrack.data.sync

import com.example.freshtrack.data.local.entities.ProductEntity

/**
 * What the sync engine needs from a remote store, with no mention of the
 * backend that provides it.
 *
 * The engine depends on this rather than on the Firestore class directly, so the
 * retry and conflict logic can be tested on the JVM without pulling in the
 * Firebase runtime — and so replacing the backend would not touch that logic.
 *
 * Implementations report failures as [RemoteError] so callers can tell a
 * temporary outage from a refused write.
 */
interface RemoteProductStore {

    suspend fun ensurePantryExists(
        pantryId: String,
        ownerUid: String,
        name: String
    ): Result<Unit>

    /** Changes strictly after [since], tombstones included. */
    suspend fun fetchChangedSince(pantryId: String, since: Long): Result<List<ProductEntity>>

    suspend fun push(pantryId: String, entities: List<ProductEntity>): Result<Unit>
}
