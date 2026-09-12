package com.example.freshtrack.data.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.freshtrack.data.local.GoodBeforeDatabase
import com.example.freshtrack.data.local.RoomTransactionRunner
import com.example.freshtrack.data.local.entities.ItemEventType
import com.example.freshtrack.data.local.entities.personalKitchenId
import com.example.freshtrack.data.remote.firestore.FirestoreRemoteStore
import com.example.freshtrack.data.repository.ItemRepositoryImpl
import com.example.freshtrack.data.session.KitchenSession
import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.ExpiryDate
import com.example.freshtrack.domain.model.Item
import com.example.freshtrack.util.AppClock
import com.example.freshtrack.util.IdGenerator
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.time.LocalDate
import java.util.UUID

/**
 * The engine and the rules together, which nothing else tests.
 *
 * Two "devices" — two in-memory databases with their own client ids — share
 * one account and one kitchen on the Firestore emulator, with the real
 * `firestore.rules` in force. Both use one of three offline; after
 * reconciling, the shelf on both must agree with the ledger on both.
 *
 * Runs only when the emulators are up (`npm run test:sync-e2e`); otherwise it
 * is skipped, so the ordinary connected run does not need them. Everything
 * here goes to a throwaway `demo-` project on a private FirebaseApp, so even
 * a mistake in this file cannot reach production.
 */
@RunWith(AndroidJUnit4::class)
class SyncEndToEndTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var uid: String
    private lateinit var kitchenId: String
    private lateinit var deviceA: Device
    private lateinit var deviceB: Device

    @Before
    fun setUp() = runBlocking {
        assumeTrue("Firestore emulator not reachable at $HOST:$FIRESTORE_PORT", emulatorsUp())

        val app = FirebaseApp.getApps(context).firstOrNull { it.name == APP_NAME }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId(PROJECT)
                    .setApplicationId("1:000000000000:android:e2e")
                    .setApiKey("fake-api-key-for-the-emulator")
                    .build(),
                APP_NAME
            )
        firestore = FirebaseFirestore.getInstance(app).apply {
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(false)
                .build()
            runCatching { useEmulator(HOST, FIRESTORE_PORT) }
        }
        auth = FirebaseAuth.getInstance(app).apply { runCatching { useEmulator(HOST, AUTH_PORT) } }

        clearFirestore()
        uid = auth.createUserWithEmailAndPassword("e2e-${UUID.randomUUID()}@example.com", "password-1")
            .await().user!!.uid
        kitchenId = personalKitchenId(uid)
        // The one thing a client may never do: flag the kitchen premium. The
        // emulator's owner endpoint stands in for the Cloud Function.
        seedPremiumKitchen()

        val remote = FirestoreRemoteStore(firestore)
        deviceA = Device("device-a", uid, remote)
        deviceB = Device("device-b", uid, remote)
    }

    @After
    fun tearDown() {
        if (::deviceA.isInitialized) deviceA.close()
        if (::deviceB.isInitialized) deviceB.close()
    }

    @Test
    fun twoDevicesUsingTheSameMilkOfflineEndUpAgreeingWithTheLedger() = runBlocking {
        // Device A buys milk and backs up. Device B comes along and gets it.
        val milkId = deviceA.repository.add(milk(quantity = 3))
        assertEquals(SyncRun.Result.SYNCED, deviceA.run.run())
        assertEquals(SyncRun.Result.SYNCED, deviceB.run.run())
        assertEquals(3, deviceB.quantityOf(milkId))

        // Both use one, offline.
        deviceA.repository.use(milkId, amount = 1)
        deviceB.repository.use(milkId, amount = 1)

        // A's push lands first; B's is rebased onto it; A picks up the result.
        assertEquals(SyncRun.Result.SYNCED, deviceA.run.run())
        assertEquals(SyncRun.Result.SYNCED, deviceB.run.run())
        assertEquals(SyncRun.Result.SYNCED, deviceA.run.run())

        // The shelf: one left, on both.
        assertEquals(1, deviceA.quantityOf(milkId))
        assertEquals(1, deviceB.quantityOf(milkId))

        // The ledger: two uses, on both, and it agrees with the shelf.
        for (device in listOf(deviceA, deviceB)) {
            val uses = device.db.itemEventDao().getAllForKitchen(kitchenId)
                .filter { it.type == ItemEventType.QUANTITY_USED }
            assertEquals(2, uses.size)
            assertEquals(2, uses.sumOf { it.quantity ?: 0 })
            assertEquals(3 - 2, device.quantityOf(milkId))
        }

        // Nothing is left waiting on either device.
        assertEquals(0, deviceA.db.outboxDao().getPendingCount(kitchenId).first())
        assertEquals(0, deviceB.db.outboxDao().getPendingCount(kitchenId).first())
    }

    @Test
    fun aDeviceThatNeverSyncedBeforeBacksUpWhatItAlreadyHas() = runBlocking {
        // Months of offline use, then premium: the first run uploads the lot,
        // and the other device sees it.
        val ids = (1..3).map { deviceA.repository.add(milk(name = "Item $it", quantity = it)) }
        deviceA.repository.use(ids[2], amount = 1)

        assertEquals(SyncRun.Result.SYNCED, deviceA.run.run())
        assertEquals(SyncRun.Result.SYNCED, deviceB.run.run())

        assertEquals(1, deviceB.quantityOf(ids[0]))
        assertEquals(2, deviceB.quantityOf(ids[1]))
        assertEquals(2, deviceB.quantityOf(ids[2]))
        assertEquals(
            deviceA.db.itemEventDao().getAllForKitchen(kitchenId).map { it.operationId }.toSet(),
            deviceB.db.itemEventDao().getAllForKitchen(kitchenId).map { it.operationId }.toSet()
        )
    }

    // ─── One "device" ───────────────────────────────────────────────────────

    private inner class Device(val clientId: String, uid: String, remote: RemoteStore) {
        val db: GoodBeforeDatabase = Room.inMemoryDatabaseBuilder(context, GoodBeforeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        private val session = object : KitchenSession {
            override fun currentUserId() = uid
            override fun isSignedIn() = true
            override fun activeKitchenId() = personalKitchenId(uid)
        }
        private val transactions = RoomTransactionRunner(db)
        private val syncState = InMemorySyncState()
        val repository = ItemRepositoryImpl(
            db.itemDao(), db.itemEventDao(), db.outboxDao(), session, transactions,
            AppClock.System, IdGenerator.Uuid, clientId, OutboxPayload::serialise
        )
        val run = SyncRun(
            session = session,
            remote = remote,
            pusher = OutboxPusher(
                db.outboxDao(), db.itemDao(), db.itemEventDao(), remote, syncState, clientId,
                OutboxPayload::deserialise, AppClock.System
            ),
            applier = RemoteChangeApplier(db.itemDao(), db.itemEventDao(), remote, syncState, transactions, clientId),
            syncState = syncState,
            clock = AppClock.System
        )

        suspend fun quantityOf(itemId: String): Int = db.itemDao().getByIdIncludingDeleted(itemId)!!.quantity

        fun close() = db.close()
    }

    private class InMemorySyncState : SyncState {
        private val flags = mutableSetOf<String>()
        private val counts = mutableMapOf<String, Long>()
        private var last: SyncRun.Result? = null
        private var lastSuccess = 0L
        override fun isBootstrapped(kitchenId: String) = "boot-$kitchenId" in flags
        override fun markBootstrapped(kitchenId: String) { flags += "boot-$kitchenId" }
        override fun bootstrapEventsUploaded(kitchenId: String) = counts["ev-$kitchenId"]?.toInt() ?: 0
        override fun setBootstrapEventsUploaded(kitchenId: String, count: Int) { counts["ev-$kitchenId"] = count.toLong() }
        override fun itemsCursor(kitchenId: String) = counts["ic-$kitchenId"] ?: 0L
        override fun setItemsCursor(kitchenId: String, cursor: Long) { counts["ic-$kitchenId"] = cursor }
        override fun eventsCursor(kitchenId: String) = counts["ec-$kitchenId"] ?: 0L
        override fun setEventsCursor(kitchenId: String, cursor: Long) { counts["ec-$kitchenId"] = cursor }
        override fun lastSuccessAt() = lastSuccess
        override fun setLastSuccessAt(at: Long) { lastSuccess = at }
        override fun lastRun() = last
        override fun setLastRun(result: SyncRun.Result) { last = result }
    }

    // The repository keeps the id it is given, so each item needs its own.
    private fun milk(name: String = "Milk", quantity: Int) = Item(
        id = UUID.randomUUID().toString(),
        name = name,
        category = "Dairy & Eggs",
        expiry = ExpiryDate.enteredByUser(LocalDate.of(2026, 9, 20), DateKind.USE_BY, atMillis = 1L),
        quantity = quantity
    )

    // ─── The emulator, over HTTP ────────────────────────────────────────────

    private val http = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private suspend fun emulatorsUp(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            http.newCall(Request.Builder().url("http://$HOST:$FIRESTORE_PORT/").build())
                .execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    private suspend fun clearFirestore() = withContext(Dispatchers.IO) {
        http("DELETE", "http://$HOST:$FIRESTORE_PORT/emulator/v1/projects/$PROJECT/databases/(default)/documents", null)
    }

    /** Written as the emulator's owner, which bypasses the rules — as a trusted server would. */
    private suspend fun seedPremiumKitchen() = withContext(Dispatchers.IO) {
        http(
            "PATCH",
            "http://$HOST:$FIRESTORE_PORT/v1/projects/$PROJECT/databases/(default)/documents/kitchens/$kitchenId",
            """
            {"fields":{
              "name":{"stringValue":"E2E kitchen"},
              "ownerUid":{"stringValue":"$uid"},
              "memberUids":{"arrayValue":{"values":[{"stringValue":"$uid"}]}},
              "isPremium":{"booleanValue":true},
              "createdAt":{"integerValue":"1"}
            }}
            """.trimIndent()
        )
    }

    private fun http(method: String, url: String, body: String?) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer owner")
            .method(method, body?.toRequestBody("application/json".toMediaType()))
            .build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "$method $url -> ${response.code}: ${response.body?.string()}" }
        }
    }

    private companion object {
        /** The host machine, as seen from the Android emulator. */
        const val HOST = "10.0.2.2"
        const val FIRESTORE_PORT = 8080
        const val AUTH_PORT = 9099
        const val PROJECT = "demo-freshtrack"
        const val APP_NAME = "e2e"
    }
}
