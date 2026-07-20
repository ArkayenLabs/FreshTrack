package com.example.freshtrack.data.account

import com.example.freshtrack.data.local.dao.ProductDao
import com.example.freshtrack.data.preferences.OnboardingPreferences
import com.example.freshtrack.data.preferences.SyncPreferences
import com.example.freshtrack.data.session.UserSession
import com.example.freshtrack.data.sync.RemoteProductStore
import com.example.freshtrack.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.runs
import io.mockk.just
import io.mockk.coVerifyOrder
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AccountDeleterTest {

    private val auth: AuthRepository = mockk(relaxed = true)
    private val remote: RemoteProductStore = mockk(relaxed = true)
    private val dao: ProductDao = mockk(relaxed = true)
    private val session: UserSession = mockk()
    private val syncPrefs: SyncPreferences = mockk(relaxed = true)
    private val onboardingPrefs: OnboardingPreferences = mockk(relaxed = true)

    private lateinit var deleter: AccountDeleter

    @Before
    fun setUp() {
        every { session.isSignedIn() } returns true
        every { session.activePantryId() } returns "personal-alice"
        every { session.currentUserId() } returns "alice"
        coEvery { remote.deleteAccountData(any(), any()) } returns Result.success(Unit)
        coEvery { auth.deleteAccount() } returns Result.success(Unit)

        deleter = AccountDeleter(auth, remote, dao, session, syncPrefs, onboardingPrefs)
    }

    @Test
    fun `deletes remote data before the auth account`() = runTest {
        // Order is the whole point: deleting the account first revokes the
        // permission needed to delete the documents, stranding them forever.
        deleter.deleteAccount()

        coVerifyOrder {
            remote.deleteAccountData("personal-alice", "alice")
            auth.deleteAccount()
        }
    }

    @Test
    fun `clears local data only after everything else succeeded`() = runTest {
        deleter.deleteAccount()

        coVerifyOrder {
            auth.deleteAccount()
            dao.deleteAllForPantry("personal-alice")
        }
    }

    @Test
    fun `a remote failure aborts before touching the account or local data`() = runTest {
        coEvery { remote.deleteAccountData(any(), any()) } returns
            Result.failure(RuntimeException("offline"))

        val result = deleter.deleteAccount()

        assertTrue(result is AccountDeleter.Result.Failed)
        // Reporting success while data remains on a server would be a worse lie
        // than an error.
        coVerify(exactly = 0) { auth.deleteAccount() }
        coVerify(exactly = 0) { dao.deleteAllForPantry(any()) }
    }

    @Test
    fun `a stale login is reported so the user can re-authenticate`() = runTest {
        coEvery { auth.deleteAccount() } returns
            Result.failure(AuthRepository.RecentLoginRequired())

        val result = deleter.deleteAccount()

        assertTrue(result is AccountDeleter.Result.NeedsRecentLogin)
    }

    @Test
    fun `local data is kept when the account deletion fails`() = runTest {
        coEvery { auth.deleteAccount() } returns Result.failure(RuntimeException("boom"))

        deleter.deleteAccount()

        // Half-erased is worse than not started: the user still has their data
        // and is still signed in.
        coVerify(exactly = 0) { dao.deleteAllForPantry(any()) }
    }

    @Test
    fun `sync watermarks are cleared on success`() = runTest {
        deleter.deleteAccount()
        coVerify { syncPrefs.clear() }
    }

    @Test
    fun `deleting while signed out fails rather than half running`() = runTest {
        every { session.isSignedIn() } returns false

        val result = deleter.deleteAccount()

        assertTrue(result is AccountDeleter.Result.Failed)
        coVerify(exactly = 0) { remote.deleteAccountData(any(), any()) }
    }
}
