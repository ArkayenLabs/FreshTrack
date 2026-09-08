package com.example.freshtrack.data.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * A stable identifier for this installation.
 *
 * Sync needs to tell "a change this device made" from "a change that arrived",
 * and a per-install id is what makes that possible without involving the user's
 * identity. It is generated locally, never derived from a device or account
 * identifier, and is reset by clearing app data — which is the correct
 * behaviour, since that is a different installation as far as the outbox is
 * concerned.
 */
class ClientIdProvider(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val clientId: String
        get() = prefs.getString(KEY_CLIENT_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_CLIENT_ID, it).apply()
        }

    private companion object {
        const val PREFS_NAME = "goodbefore_client"
        const val KEY_CLIENT_ID = "client_id"
    }
}
