package com.example.freshtrack.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages onboarding completion state and guest mode using SharedPreferences
 */
class OnboardingPreferences(context: Context) {

    private val masterKey = androidx.security.crypto.MasterKey.Builder(context)
        .setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = androidx.security.crypto.EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val PREFS_NAME = "freshtrack_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
        private const val KEY_GUEST_MODE = "guest_mode"
        private const val KEY_ASKED_ABOUT_REMINDERS = "asked_about_reminders"
    }

    /**
     * Check if user has completed onboarding
     */
    fun isOnboardingCompleted(): Boolean {
        return sharedPreferences.getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    /**
     * Mark onboarding as completed
     */
    fun setOnboardingCompleted() {
        sharedPreferences.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, true)
            .apply()
    }

    /**
     * Reset onboarding state (useful for testing)
     */
    fun resetOnboarding() {
        sharedPreferences.edit()
            .putBoolean(KEY_ONBOARDING_COMPLETED, false)
            .apply()
    }

    /**
     * Whether the reminder prompt has already been put to this person.
     *
     * Asked at most once. Someone who said no is not asked again on the next
     * launch, the next item, or the next time the screen happens to recompose;
     * repeating the question is how a permission prompt turns into nagging.
     */
    fun hasAskedAboutReminders(): Boolean =
        sharedPreferences.getBoolean(KEY_ASKED_ABOUT_REMINDERS, false)

    /** Records that the question has been asked, whatever the answer was. */
    fun setAskedAboutReminders() {
        sharedPreferences.edit().putBoolean(KEY_ASKED_ABOUT_REMINDERS, true).apply()
    }

    /** Returns true if user chose to skip auth and use the app as a guest */
    fun isGuestMode(): Boolean = sharedPreferences.getBoolean(KEY_GUEST_MODE, false)

    /** Enter or exit guest mode */
    fun setGuestMode(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_GUEST_MODE, enabled).apply()
    }
}