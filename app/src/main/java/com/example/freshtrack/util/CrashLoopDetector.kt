package com.example.freshtrack.util

import android.content.Context
import android.content.SharedPreferences
import java.io.File

class CrashLoopDetector(private val context: Context) {

    companion object {
        private const val PREFS_NAME = "crash_loop_detector"
        private const val KEY_CRASH_COUNT = "crash_count"
        private const val KEY_APP_RESET = "app_was_reset"
        private const val MAX_CRASHES_BEFORE_RESET = 3
        private const val STARTUP_GRACE_PERIOD_MS = 5000L
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private var startupTimestamp: Long = 0

    fun onAppStarting(): Boolean {
        startupTimestamp = System.currentTimeMillis()
        
        val crashCount = prefs.getInt(KEY_CRASH_COUNT, 0) + 1
        prefs.edit().putInt(KEY_CRASH_COUNT, crashCount).apply()
        
        if (crashCount >= MAX_CRASHES_BEFORE_RESET) {
            performEmergencyReset()
            return true
        }
        
        return false
    }

    fun onAppStartupComplete() {
        val elapsedTime = System.currentTimeMillis() - startupTimestamp
        if (elapsedTime >= STARTUP_GRACE_PERIOD_MS) {
            resetCrashCount()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                resetCrashCount()
            }, STARTUP_GRACE_PERIOD_MS - elapsedTime)
        }
    }

    private fun resetCrashCount() {
        prefs.edit().putInt(KEY_CRASH_COUNT, 0).apply()
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
            .apply()
    }

    private fun clearAppData() {
        try {
            context.cacheDir?.deleteRecursively()
            
            val dataDir = context.filesDir?.parentFile
            dataDir?.listFiles()?.forEach { file ->
                if (file.name != "shared_prefs") {
                    file.deleteRecursively()
                }
            }
            
            context.getSharedPreferences("freshtrack_preferences", Context.MODE_PRIVATE)
                .edit().clear().apply()
                
            context.deleteDatabase("freshtrack_database")
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun File.deleteRecursively(): Boolean {
        if (isDirectory) {
            listFiles()?.forEach { it.deleteRecursively() }
        }
        return delete()
    }
}
