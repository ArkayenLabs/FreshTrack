package com.example.freshtrack.data.remote.firestore

import com.example.freshtrack.data.sync.RemoteDocument
import com.example.freshtrack.data.sync.RemoteError
import com.example.freshtrack.data.sync.RemoteStore
import com.example.freshtrack.data.sync.RemoteWrite
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.tasks.await

/**
 * Firestore access for kitchens, their items and their events.
 *
 * Every call can fail — offline, permission denied, quota. Failures are
 * returned as [Result] rather than thrown, because sync must never take the
 * app down with it.
 *
 * `serverUpdatedAt` is stamped here with the server's clock on every write.
 * The rules refuse anything else, and it is what the pull cursor orders by.
 */
class FirestoreRemoteStore(
    private val firestore: FirebaseFirestore
) : RemoteStore {

    private fun kitchen(kitchenId: String) = firestore.collection(KITCHENS).document(kitchenId)
    private fun items(kitchenId: String): CollectionReference = kitchen(kitchenId).collection(ITEMS)
    private fun events(kitchenId: String): CollectionReference = kitchen(kitchenId).collection(EVENTS)

    /**
     * `isPremium` is never written here — the rules reject a client that
     * tries, and only a verified purchase may set it.
     */
    override suspend fun ensureKitchenExists(
        kitchenId: String,
        ownerUid: String,
        name: String
    ): Result<Unit> = runCatching {
        val ref = kitchen(kitchenId)
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

    override suspend fun isKitchenPremium(kitchenId: String): Result<Boolean> = runCatching {
        kitchen(kitchenId).get().await().getBoolean("isPremium") == true
    }.mapRemoteError()

    override suspend fun push(write: RemoteWrite): Result<Unit> = runCatching {
        val stamp = FieldValue.serverTimestamp()
        firestore.batch()
            .set(items(write.kitchenId).document(write.itemId), write.itemFields + ("serverUpdatedAt" to stamp))
            .set(events(write.kitchenId).document(write.operationId), write.eventFields + ("serverUpdatedAt" to stamp))
            .commit()
            .await()
        Unit
    }.mapRemoteError()

    override suspend fun eventExists(kitchenId: String, operationId: String): Result<Boolean> =
        runCatching { events(kitchenId).document(operationId).get().await().exists() }
            .mapRemoteError()

    override suspend fun pushItems(
        kitchenId: String,
        items: List<Pair<String, Map<String, Any?>>>
    ): Result<Unit> = runCatching {
        items.chunked(RemoteStore.MAX_BATCH).forEach { chunk ->
            val stamp = FieldValue.serverTimestamp()
            val batch = firestore.batch()
            chunk.forEach { (id, fields) ->
                batch.set(items(kitchenId).document(id), fields + ("serverUpdatedAt" to stamp))
            }
            batch.commit().await()
        }
    }.mapRemoteError()

    override suspend fun pushEvents(
        kitchenId: String,
        events: List<Pair<String, Map<String, Any?>>>
    ): Result<Unit> = runCatching {
        require(events.size <= RemoteStore.MAX_BATCH) { "one batch at a time, so progress can be recorded" }
        val stamp = FieldValue.serverTimestamp()
        val batch = firestore.batch()
        events.forEach { (operationId, fields) ->
            batch.set(events(kitchenId).document(operationId), fields + ("serverUpdatedAt" to stamp))
        }
        batch.commit().await()
        Unit
    }.mapRemoteError()

    override suspend fun fetchItem(kitchenId: String, itemId: String): Result<RemoteDocument?> =
        runCatching {
            val doc = items(kitchenId).document(itemId).get().await()
            val stamp = doc.getTimestamp(SERVER_UPDATED_AT) ?: return@runCatching null
            val fields = doc.data ?: return@runCatching null
            RemoteDocument(doc.id, fields - SERVER_UPDATED_AT, microsOf(stamp))
        }.mapRemoteError()

    override suspend fun fetchItemsSince(kitchenId: String, after: Long, limit: Int) =
        fetchSince(items(kitchenId), after, limit)

    override suspend fun fetchEventsSince(kitchenId: String, after: Long, limit: Int) =
        fetchSince(events(kitchenId), after, limit)

    private suspend fun fetchSince(
        collection: CollectionReference,
        after: Long,
        limit: Int
    ): Result<List<RemoteDocument>> = runCatching {
        collection
            .whereGreaterThan(SERVER_UPDATED_AT, timestampOf(after))
            .orderBy(SERVER_UPDATED_AT, Query.Direction.ASCENDING)
            .limit(limit.toLong())
            .get()
            .await()
            .documents
            .mapNotNull { doc ->
                val stamp = doc.getTimestamp(SERVER_UPDATED_AT) ?: return@mapNotNull null
                val fields = doc.data ?: return@mapNotNull null
                RemoteDocument(doc.id, fields - SERVER_UPDATED_AT, microsOf(stamp))
            }
    }.mapRemoteError()

    override suspend fun deleteAccountData(kitchenId: String, uid: String): Result<Unit> =
        runCatching {
            deleteAll(items(kitchenId))
            deleteAll(events(kitchenId))
            kitchen(kitchenId).delete().await()
            firestore.collection(USERS).document(uid).delete().await()
            Unit
        }.mapRemoteError()

    /** Pages through a subcollection rather than assuming it is small. */
    private suspend fun deleteAll(collection: CollectionReference) {
        while (true) {
            val page = collection.limit(BATCH_LIMIT.toLong()).get().await()
            if (page.isEmpty) break

            val batch = firestore.batch()
            page.documents.forEach { batch.delete(it.reference) }
            batch.commit().await()

            if (page.size() < BATCH_LIMIT) break
        }
    }

    /**
     * Translates Firestore's exception vocabulary into [RemoteError] so the
     * sync engine never has to import a Firebase type.
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

    // A Firestore timestamp is seconds and nanoseconds. Microseconds are the
    // finest unit that fits a Long for any plausible date and that the server
    // actually distinguishes, so the cursor round-trips exactly.
    private fun microsOf(stamp: Timestamp): Long =
        stamp.seconds * 1_000_000L + stamp.nanoseconds / 1_000L

    private fun timestampOf(micros: Long): Timestamp =
        Timestamp(micros / 1_000_000L, ((micros % 1_000_000L) * 1_000L).toInt())

    companion object {
        private val RETRYABLE_CODES = setOf(
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED,
            FirebaseFirestoreException.Code.ABORTED,
            FirebaseFirestoreException.Code.INTERNAL
        )

        private const val KITCHENS = "kitchens"
        private const val USERS = "users"
        private const val ITEMS = "items"
        private const val EVENTS = "events"
        private const val SERVER_UPDATED_AT = "serverUpdatedAt"
        private const val BATCH_LIMIT = 500
    }
}
