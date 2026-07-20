package com.example.freshtrack.data.account

import com.example.freshtrack.data.local.dao.ProductDao
import com.example.freshtrack.data.preferences.OnboardingPreferences
import com.example.freshtrack.data.preferences.SyncPreferences
import com.example.freshtrack.data.session.UserSession
import com.example.freshtrack.data.sync.RemoteProductStore
import com.example.freshtrack.domain.repository.AuthRepository

/**
 * Deletes a user's account and everything belonging to it.
 *
 * Order matters. Remote data goes first, while the user is still authenticated
 * and the security rules still recognise them as the pantry owner. Deleting the
 * Firebase account first would revoke that permission and strand every document
 * in Firestore with no owner and no way to reach it — the exact retention
 * failure the deletion requirement exists to prevent.
 *
 * Local data is cleared last, so a failure part-way through leaves the user
 * signed in with their data intact rather than half-erased.
 */
class AccountDeleter(
    private val authRepository: AuthRepository,
    private val remote: RemoteProductStore,
    private val productDao: ProductDao,
    private val session: UserSession,
    private val syncPrefs: SyncPreferences,
    private val onboardingPrefs: OnboardingPreferences
) {

    sealed interface Result {
        data object Success : Result

        /** Firebase wants a fresh sign-in before it will delete the account. */
        data object NeedsRecentLogin : Result

        data class Failed(val cause: Throwable) : Result
    }

    suspend fun deleteAccount(): Result {
        if (!session.isSignedIn()) {
            return Result.Failed(IllegalStateException("Not signed in"))
        }

        val pantryId = session.activePantryId()
        val uid = session.currentUserId()

        // 1. Remote first, while the account still has permission to do it.
        //    A failure here is reported rather than swallowed: claiming an
        //    account was deleted while its data remains on a server would be a
        //    worse lie than an error message.
        remote.deleteAccountData(pantryId, uid)
            .onFailure { return Result.Failed(it) }

        // 2. The Firebase account itself. If this needs a fresh sign-in, stop
        //    and say so — remote data is already gone, and retrying after
        //    re-authentication is harmless because deletion is idempotent.
        authRepository.deleteAccount()
            .onFailure { error ->
                return if (error is AuthRepository.RecentLoginRequired) {
                    Result.NeedsRecentLogin
                } else {
                    Result.Failed(error)
                }
            }

        // 3. Local last. Everything here is a hard delete: this is the one place
        //    tombstones are pointless, because there is no longer an account for
        //    them to propagate to.
        productDao.deleteAllForPantry(pantryId)
        syncPrefs.clear()
        onboardingPrefs.setGuestMode(false)

        return Result.Success
    }
}
