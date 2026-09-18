package com.example.freshtrack.data.preferences

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores the user's decision on analytics and crash reporting.
 *
 * Two facts are kept separately: whether the user has been asked at all, and
 * what they chose. "Not asked yet" must never be treated as consent — the
 * default is off, and stays off until the user actively turns it on.
 *
 * This is the source of truth the app applies on every launch. The manifest
 * flags stop Firebase collecting *before* this can be read; this then decides
 * whether to switch it on.
 */
class ConsentPreferences(context: Context) {

    private val prefs: SharedPreferences = encryptedPreferences(context, PREFS_NAME)

    /** Whether the analytics consent prompt has been answered at all. */
    fun hasDecided(): Boolean = prefs.contains(KEY_ANALYTICS_GRANTED)

    /** The user's choice. Defaults to false — off until explicitly granted. */
    fun isAnalyticsGranted(): Boolean = prefs.getBoolean(KEY_ANALYTICS_GRANTED, false)

    fun setAnalyticsConsent(granted: Boolean) {
        prefs.edit().putBoolean(KEY_ANALYTICS_GRANTED, granted).apply()
    }

    companion object {
        private const val PREFS_NAME = "freshtrack_consent_prefs"
        private const val KEY_ANALYTICS_GRANTED = "analytics_granted"
    }
}
