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
