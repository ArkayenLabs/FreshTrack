package com.example.freshtrack.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Sync watermarks, kept per pantry so joining a household later does not reset
 * the personal pantry's position.
 *
 * Pull and push are tracked separately. Sharing one watermark would mean a row
 * just pulled from the server immediately looks like a local change and gets
 * pushed straight back.
 */
class SyncPreferences(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun lastPulledAt(pantryId: String): Long = prefs.getLong(pullKey(pantryId), 0L)

    fun setLastPulledAt(pantryId: String, value: Long) {
        prefs.edit().putLong(pullKey(pantryId), value).apply()
    }

    fun lastPushedAt(pantryId: String): Long = prefs.getLong(pushKey(pantryId), 0L)

    fun setLastPushedAt(pantryId: String, value: Long) {
        prefs.edit().putLong(pushKey(pantryId), value).apply()
    }

    /**
     * When a sync last completed, for display. Not per pantry: the user is
     * being told "your data is backed up", not asked to reason about pantries.
     */
    fun lastSuccessAt(): Long = prefs.getLong(KEY_LAST_SUCCESS, 0L)

    fun setLastSuccessAt(value: Long) {
        prefs.edit().putLong(KEY_LAST_SUCCESS, value).apply()
    }

    /** Used when a user signs out, so the next account starts clean. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun pullKey(pantryId: String) = "last_pulled_$pantryId"
    private fun pushKey(pantryId: String) = "last_pushed_$pantryId"

    companion object {
        private const val PREFS_NAME = "freshtrack_sync_prefs"
        private const val KEY_LAST_SUCCESS = "last_success_at"
    }
}
