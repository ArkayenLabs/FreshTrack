package com.example.freshtrack.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages onboarding completion state using SharedPreferences
 */
class OnboardingPreferences(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "freshtrack_prefs"
        private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"
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
}