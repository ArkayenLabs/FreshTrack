package com.example.freshtrack.data.remote.firestore

import com.example.freshtrack.data.local.entities.ProductEntity
import com.example.freshtrack.data.sync.RemoteError
import com.example.freshtrack.data.sync.RemoteProductStore
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

/**
 * Firestore access for pantries and their products.
 *
 * Every call can fail — offline, permission denied, quota. Failures are returned
 * as [Result] rather than thrown, because sync must never take the app down with
 * it.
 */
class RemoteProductDataSource(
    private val firestore: FirebaseFirestore
) : RemoteProductStore {
    private fun products(pantryId: String) =
        firestore.collection(PANTRIES).document(pantryId).collection(PRODUCTS)

    /**
     * Creates the pantry document if it is not already there.
     *
     * Uses the derived id, so calling this repeatedly is harmless. `isPremium`
     * is never written here — the rules reject a client that tries, and only a
     * verified purchase may set it.
     */
    override suspend fun ensurePantryExists(
        pantryId: String,
        ownerUid: String,
        name: String
    ): Result<Unit> = runCatching {
        val ref = firestore.collection(PANTRIES).document(pantryId)
        if (ref.get().await().exists()) return@runCatching
        ref.set(
            mapOf(
                "name" to name,
                "ownerUid" to ownerUid,
                "memberUids" to listOf(ownerUid),
                "createdAt" to System.currentTimeMillis()
            )
        ).await()
    }.mapRemoteError()

    /**
     * Products changed strictly after [since], tombstones included — a deletion
     * has to arrive like any other change.
     */
    override suspend fun fetchChangedSince(
        pantryId: String,
        since: Long
    ): Result<List<ProductEntity>> = runCatching {
        products(pantryId)
            .whereGreaterThan(ProductFields.UPDATED_AT, since)
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                doc.data?.let { productFromFirestore(doc.id, pantryId, it) }
            }
    }.mapRemoteError()

    /**
     * Writes local rows up. Merges rather than replaces, so a field this client
     * does not know about — written by a newer app version on another device —
     * is not wiped out by an older one.
     */
    override suspend fun push(pantryId: String, entities: List<ProductEntity>): Result<Unit> =
        runCatching {
            if (entities.isEmpty()) return@runCatching
            // Firestore caps a batch at 500 writes.
            entities.chunked(BATCH_LIMIT).forEach { chunk ->
                val batch = firestore.batch()
                chunk.forEach { entity ->
                    batch.set(
                        products(pantryId).document(entity.id),
                        entity.toFirestoreMap(),
                        SetOptions.merge()
                    )
                }
                batch.commit().await()
            }
        }.mapRemoteError()

    override suspend fun deleteAccountData(pantryId: String, uid: String): Result<Unit> =
        runCatching {
            // Page through the subcollection rather than assuming it is small.
            while (true) {
                val page = products(pantryId).limit(BATCH_LIMIT.toLong()).get().await()
                if (page.isEmpty) break

                val batch = firestore.batch()
                page.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()

                if (page.size() < BATCH_LIMIT) break
            }

            firestore.collection(PANTRIES).document(pantryId).delete().await()
            firestore.collection(USERS).document(uid).delete().await()
            Unit
        }.mapRemoteError()

    /**
     * Translates Firestore's exception vocabulary into [RemoteError] so the sync
     * engine never has to import a Firebase type.
     */
    private fun classify(t: Throwable): RemoteError = when {
        t is FirebaseFirestoreException &&
            t.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ->
            RemoteError.PermissionDenied(t)

        t is FirebaseFirestoreException && t.code in RETRYABLE_CODES ->
            RemoteError.Transient(t)

        else -> RemoteError.Permanent(t)
    }

    private fun <T> Result<T>.mapRemoteError(): Result<T> =
        fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(classify(it)) })

    companion object {
        private val RETRYABLE_CODES = setOf(
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
            FirebaseFirestoreException.Code.ABORTED,
            FirebaseFirestoreException.Code.INTERNAL
        )

        private const val PANTRIES = "pantries"
        private const val USERS = "users"
        private const val PRODUCTS = "products"
        private const val BATCH_LIMIT = 500
    }
}
