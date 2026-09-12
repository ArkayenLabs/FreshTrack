package com.example.freshtrack.data.preferences

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.freshtrack.data.sync.SyncRun
import com.example.freshtrack.data.sync.SyncState

/**
 * Sync progress, kept per kitchen so joining a household later does not reset
 * the personal kitchen's position.
 *
 * The previous watermark pair (last pulled at, last pushed at) is gone with
 * the polling client it served; the outbox is the pending set now, and the
 * only per-kitchen state left is how far a first backup has got.
 */
class SyncPreferences(context: Context) : SyncState {

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

    override fun isBootstrapped(kitchenId: String): Boolean =
        prefs.getBoolean(bootstrappedKey(kitchenId), false)

    override fun markBootstrapped(kitchenId: String) {
        prefs.edit().putBoolean(bootstrappedKey(kitchenId), true).apply()
    }

    override fun bootstrapEventsUploaded(kitchenId: String): Int =
        prefs.getInt(eventsUploadedKey(kitchenId), 0)

    override fun setBootstrapEventsUploaded(kitchenId: String, count: Int) {
        prefs.edit().putInt(eventsUploadedKey(kitchenId), count).apply()
    }

    override fun itemsCursor(kitchenId: String): Long = prefs.getLong("items_cursor_$kitchenId", 0L)

    override fun setItemsCursor(kitchenId: String, cursor: Long) {
        prefs.edit().putLong("items_cursor_$kitchenId", cursor).apply()
    }

    override fun eventsCursor(kitchenId: String): Long = prefs.getLong("events_cursor_$kitchenId", 0L)

    override fun setEventsCursor(kitchenId: String, cursor: Long) {
        prefs.edit().putLong("events_cursor_$kitchenId", cursor).apply()
    }

    /**
     * When a sync last completed, for display. Not per kitchen: the user is
     * being told "your data is backed up", not asked to reason about kitchens.
     */
    override fun lastSuccessAt(): Long = prefs.getLong(KEY_LAST_SUCCESS, 0L)

    override fun setLastSuccessAt(at: Long) {
        prefs.edit().putLong(KEY_LAST_SUCCESS, at).apply()
    }

    override fun lastRun(): SyncRun.Result? =
        prefs.getString(KEY_LAST_RUN, null)?.let { runCatching { SyncRun.Result.valueOf(it) }.getOrNull() }

    override fun setLastRun(result: SyncRun.Result) {
        prefs.edit().putString(KEY_LAST_RUN, result.name).apply()
    }

    /** Used when a user signs out, so the next account starts clean. */
    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun bootstrappedKey(kitchenId: String) = "bootstrapped_$kitchenId"
    private fun eventsUploadedKey(kitchenId: String) = "bootstrap_events_$kitchenId"

    companion object {
        private const val PREFS_NAME = "freshtrack_sync_prefs"
        private const val KEY_LAST_SUCCESS = "last_success_at"
        private const val KEY_LAST_RUN = "last_run"
    }
}
