package com.example.freshtrack.data.sync

import com.example.freshtrack.data.local.dao.ProductDao
import com.example.freshtrack.data.local.entities.ProductEntity
import com.example.freshtrack.data.preferences.SyncPreferences
import com.example.freshtrack.data.session.UserSession
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The sync engine's rules, exercised without a network: last-write-wins, no
 * echoing back what was just pulled, and watermarks that only advance on
 * success.
 */
class ProductSyncerTest {

    private val dao: ProductDao = mockk(relaxed = true)
    private val remote: RemoteProductStore = mockk(relaxed = true)
    private val session: UserSession = mockk()
    private val prefs: SyncPreferences = mockk(relaxed = true)

    private lateinit var syncer: ProductSyncer

    private val pantryId = "personal-alice"

    private fun product(
        id: String,
        updatedAt: Long,
        name: String = "Milk",
        imageUri: String? = null,
        isDeleted: Boolean = false
    ) = ProductEntity(
        id = id,
        pantryId = pantryId,
        userId = "alice",
        name = name,
        category = "Dairy",
        expiryDate = 999,
        updatedAt = updatedAt,
        imageUri = imageUri,
        isDeleted = isDeleted
    )

    @Before
    fun setUp() {
        every { session.isSignedIn() } returns true
        every { session.activePantryId() } returns pantryId
        every { session.currentUserId() } returns "alice"
        coEvery { remote.ensurePantryExists(any(), any(), any()) } returns Result.success(Unit)
        coEvery { remote.fetchChangedSince(any(), any()) } returns Result.success(emptyList())
        coEvery { remote.push(any(), any()) } returns Result.success(Unit)
        every { prefs.lastPulledAt(any()) } returns 0L
        every { prefs.lastPushedAt(any()) } returns 0L
        coEvery { dao.getChangedSince(any(), any()) } returns emptyList()
        coEvery { dao.getByIdIncludingDeleted(any()) } returns null

        syncer = ProductSyncer(dao, remote, session, prefs)
    }

    @Test
    fun `signed out does nothing`() = runTest {
        every { session.isSignedIn() } returns false

        val result = syncer.sync()

        assertEquals(SyncResult.SkippedSignedOut, result)
        coVerify(exactly = 0) { remote.push(any(), any()) }
    }

    @Test
    fun `remote row newer than local is applied`() = runTest {
        val incoming = product("milk", updatedAt = 200)
        coEvery { remote.fetchChangedSince(any(), any()) } returns Result.success(listOf(incoming))
        coEvery { dao.getByIdIncludingDeleted("milk") } returns product("milk", updatedAt = 100)

        val applied = slot<List<ProductEntity>>()
        coEvery { dao.upsertFromRemote(capture(applied)) } returns Unit

        syncer.sync()

        assertEquals(1, applied.captured.size)
        assertEquals(200L, applied.captured.first().updatedAt)
    }

    @Test
    fun `local row newer than remote is not overwritten`() = runTest {
        val incoming = product("milk", updatedAt = 100)
        coEvery { remote.fetchChangedSince(any(), any()) } returns Result.success(listOf(incoming))
        coEvery { dao.getByIdIncludingDeleted("milk") } returns product("milk", updatedAt = 500)

        syncer.sync()

        coVerify(exactly = 0) { dao.upsertFromRemote(any()) }
    }

    @Test
    fun `equal timestamps leave local alone`() = runTest {
        val incoming = product("milk", updatedAt = 300)
        coEvery { remote.fetchChangedSince(any(), any()) } returns Result.success(listOf(incoming))
        coEvery { dao.getByIdIncludingDeleted("milk") } returns product("milk", updatedAt = 300)

        syncer.sync()

        coVerify(exactly = 0) { dao.upsertFromRemote(any()) }
    }

    @Test
    fun `pulled row is not pushed straight back`() = runTest {
        val incoming = product("milk", updatedAt = 200)
        coEvery { remote.fetchChangedSince(any(), any()) } returns Result.success(listOf(incoming))
        coEvery { dao.getByIdIncludingDeleted("milk") } returns null
        // The DAO would report it as a local change, because applying it wrote it.
        coEvery { dao.getChangedSince(any(), any()) } returns listOf(incoming)

        syncer.sync()

        coVerify(exactly = 0) { remote.push(any(), any()) }
    }

    @Test
    fun `a genuinely local change is pushed`() = runTest {
        val local = product("bread", updatedAt = 400)
        coEvery { dao.getChangedSince(any(), any()) } returns listOf(local)

        val pushed = slot<List<ProductEntity>>()
        coEvery { remote.push(any(), capture(pushed)) } returns Result.success(Unit)

        val result = syncer.sync()

        assertEquals(listOf("bread"), pushed.captured.map { it.id })
        assertTrue(result is SyncResult.Success)
    }

    @Test
    fun `tombstones are pushed like any other change`() = runTest {
        val deleted = product("gone", updatedAt = 600, isDeleted = true)
        coEvery { dao.getChangedSince(any(), any()) } returns listOf(deleted)

        val pushed = slot<List<ProductEntity>>()
        coEvery { remote.push(any(), capture(pushed)) } returns Result.success(Unit)

        syncer.sync()

        assertTrue(pushed.captured.single().isDeleted)
    }

    @Test
    fun `local image path survives a pull`() = runTest {
        // The remote document carries no image path on purpose; a pull must not
        // wipe the one this device has.
        val incoming = product("milk", updatedAt = 200, imageUri = null)
        coEvery { remote.fetchChangedSince(any(), any()) } returns Result.success(listOf(incoming))
        coEvery { dao.getByIdIncludingDeleted("milk") } returns
            product("milk", updatedAt = 100, imageUri = "file:///local/photo.jpg")

        val applied = slot<List<ProductEntity>>()
        coEvery { dao.upsertFromRemote(capture(applied)) } returns Unit

        syncer.sync()

        assertEquals("file:///local/photo.jpg", applied.captured.single().imageUri)
    }

    @Test
    fun `watermarks do not advance when the push fails`() = runTest {
        coEvery { dao.getChangedSince(any(), any()) } returns listOf(product("bread", 400))
        coEvery { remote.push(any(), any()) } returns
            Result.failure(RuntimeException("network went away"))

        val result = syncer.sync()

        assertTrue(result is SyncResult.Failed || result is SyncResult.Retryable)
        coVerify(exactly = 0) { prefs.setLastPushedAt(any(), any()) }
        coVerify(exactly = 0) { prefs.setLastPulledAt(any(), any()) }
    }

    @Test
    fun `pull watermark advances to the newest row seen`() = runTest {
        coEvery { remote.fetchChangedSince(any(), any()) } returns Result.success(
            listOf(product("a", 100), product("b", 900), product("c", 500))
        )

        syncer.sync()

        coVerify { prefs.setLastPulledAt(pantryId, 900L) }
    }
}
