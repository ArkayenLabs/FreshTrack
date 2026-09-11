package com.example.freshtrack.util

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class CrashLoopDetector(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "crash_loop_detector"
        private const val KEY_CRASH_COUNT = "crash_count"
        private const val KEY_APP_RESET = "app_was_reset"
        private const val KEY_APP_RUNNING = "app_is_running"
        private const val KEY_LAST_CRASH_TIME = "last_crash_time"
        private const val MAX_CRASHES_BEFORE_RESET = 3
        private const val CRASH_WINDOW_MS = 60000L

        // Named here rather than referenced from the preference classes so that
        // clearing a file never constructs one — building an
        // EncryptedSharedPreferences is exactly the kind of work that can fail
        // during the start we are recovering.
        private const val PREFS_ONBOARDING = "freshtrack_prefs"
        private const val PREFS_CONSENT = "freshtrack_consent_prefs"
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun onAppStarting(): Boolean {
        val wasRunning = prefs.getBoolean(KEY_APP_RUNNING, false)
        val currentTime = System.currentTimeMillis()
        val lastCrashTime = prefs.getLong(KEY_LAST_CRASH_TIME, 0)
        
        prefs.edit().putBoolean(KEY_APP_RUNNING, true).apply()
        
        if (wasRunning) {
            val timeSinceLastCrash = currentTime - lastCrashTime
            if (timeSinceLastCrash > CRASH_WINDOW_MS) {
                prefs.edit().putInt(KEY_CRASH_COUNT, 0).apply()
            }
            
            val crashCount = prefs.getInt(KEY_CRASH_COUNT, 0) + 1
            prefs.edit()
                .putInt(KEY_CRASH_COUNT, crashCount)
                .putLong(KEY_LAST_CRASH_TIME, currentTime)
                .apply()
            
            if (crashCount >= MAX_CRASHES_BEFORE_RESET) {
                performEmergencyReset()
                return true
            }
        }
        
        return false
    }

    fun onAppRunningStable() {
        prefs.edit()
            .putInt(KEY_CRASH_COUNT, 0)
            .putLong(KEY_LAST_CRASH_TIME, 0)
            .apply()
    }

    fun onAppExitCleanly() {
        prefs.edit().putBoolean(KEY_APP_RUNNING, false).apply()
    }

    fun wasAppReset(): Boolean {
        val wasReset = prefs.getBoolean(KEY_APP_RESET, false)
        if (wasReset) {
            prefs.edit().putBoolean(KEY_APP_RESET, false).apply()
        }
        return wasReset
    }

    private fun performEmergencyReset() {
        clearAppData()
        prefs.edit()
            .putInt(KEY_CRASH_COUNT, 0)
            .putBoolean(KEY_APP_RESET, true)
            .putBoolean(KEY_APP_RUNNING, false)
            .putLong(KEY_LAST_CRASH_TIME, 0)
            .apply()
    }

    /**
     * Clears what can be rebuilt, and nothing else.
     *
     * This deliberately does NOT touch the database. Room is the source of
     * truth for an offline-first app, most users are guests with no cloud
     * backup, and the event ledger cannot be reconstructed from anywhere — so
     * wiping it to escape a crash loop costs the user the entire contents of
     * their kitchen and every bit of impact history the app has told them
     * about. A crash is recoverable; that is not.
     *
     * The previous version had it exactly backwards: it deleted the whole data
     * directory apart from `shared_prefs`, destroying the database and files/
     * while preserving the preferences — and a bad preference read at startup
     * is far more likely to loop than the database is. It also cleared
     * "freshtrack_preferences", which is not a file this app has ever written,
     * and called deleteDatabase("freshtrack_database"), a name retired in the
     * GoodBefore migration. So the two lines that looked like the targeted part
     * of the reset were both no-ops, and only the indiscriminate wipe did
     * anything.
     */
    private fun clearAppData() {
        try {
            context.cacheDir?.clearContents()
            context.codeCacheDir?.clearContents()

            // Onboarding and consent state: recoverable (the user sees
            // onboarding again, and consent returns to its manifest default of
            // off, which is the safe direction) and a plausible cause of a
            // start-up loop.
            clearPreferences(PREFS_ONBOARDING)
            clearPreferences(PREFS_CONSENT)
        } catch (e: Exception) {
            // Recovery is best-effort by nature: if we cannot clear a cache
            // there is nothing further to do, and throwing here would crash the
            // start we are trying to rescue.
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                .recordException(e)
        }
    }

    /**
     * Clears one preference file.
     *
     * Both files are written through EncryptedSharedPreferences, but clearing
     * is done on the plain handle to the same file: opening the encrypted view
     * needs the keystore key, and a key that has been invalidated is itself a
     * reason the app might be looping. Deleting the entries does not need to
     * read them.
     */
    private fun clearPreferences(name: String) {
        runCatching {
            context.getSharedPreferences(name, Context.MODE_PRIVATE)
                .edit()
                .clear()
                .commit()
        }
    }

    /**
     * Empties a directory but keeps the directory itself.
     *
     * The framework hands out cacheDir and codeCacheDir as long-lived paths;
     * deleting the directory out from under them leaves anything already
     * holding one writing into a path that no longer exists.
     */
    private fun File.clearContents() {
        if (!isDirectory) return
        listFiles()?.forEach { it.deleteTree() }
    }

    private fun File.deleteTree(): Boolean {
        if (isDirectory) {
            listFiles()?.forEach { it.deleteTree() }
        }
        return delete()
    }
}
