package com.example.freshtrack.data.preferences

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.IOException
import java.security.GeneralSecurityException

/**
 * Opens an [EncryptedSharedPreferences] file, and starts it over if it cannot
 * be read.
 *
 * The file holds its own key set, wrapped by a master key that lives only in
 * the device keystore. A backup restore, a keystore wiped by a lock-screen
 * change on some devices, or an OEM keystore bug leaves the file present and
 * the key gone, and `create` throws `AEADBadTagException` — from
 * `Application.onCreate`, before any screen, on every launch. Seen on a real
 * device on 16 Sep 2026 after a reinstall.
 *
 * Nothing in these files is worth a crash loop: consent reverts to off, the
 * onboarding and guest flags re-derive, sync cursors reset and the next run
 * re-pulls (the outbox lives in Room, so no change is lost), and the client
 * id is attribution only. So a file that cannot be opened is deleted and
 * recreated empty, and the loss is logged. The backup rules exclude every
 * one of these files so the common cause does not arise; this is for the
 * causes the rules cannot prevent.
 */
internal fun encryptedPreferences(
    context: Context,
    name: String,
    /** Only a test passes this, so it can lose a key without touching the app's own files. */
    keyAlias: String = MasterKey.DEFAULT_MASTER_KEY_ALIAS
): SharedPreferences =
    try {
        create(context, name, keyAlias)
    } catch (e: GeneralSecurityException) {
        recover(context, name, keyAlias, e)
    } catch (e: IOException) {
        recover(context, name, keyAlias, e)
    }

private fun recover(context: Context, name: String, keyAlias: String, cause: Exception): SharedPreferences {
    Log.w(TAG, "Preferences '$name' could not be decrypted; starting over", cause)
    context.deleteSharedPreferences(name)
    return create(context, name, keyAlias)
}

private fun create(context: Context, name: String, keyAlias: String): SharedPreferences =
    EncryptedSharedPreferences.create(
        context,
        name,
        MasterKey.Builder(context, keyAlias).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

private const val TAG = "EncryptedPreferences"
