package com.example.freshtrack.data.preferences

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore

/**
 * The two ways an EncryptedSharedPreferences file becomes unreadable, each
 * reproduced for real on the device, and the opener the app uses shown to
 * start over from both:
 *
 * - The keystore master key is gone but the file remains — what a reinstall
 *   with backup restore does. Reproduced by deleting the key. This is the
 *   `AEADBadTagException` that crashed the app on launch on 16 Sep 2026.
 * - The key set inside the file is not a key set at all. Reproduced by
 *   overwriting it. This surfaces as an `IOException`, not a security one.
 *
 * A test-only master key alias keeps all of this away from the app's own
 * files on the device the test runs on.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedPreferencesRecoveryTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "recovery_test_prefs"
    private val keyAlias = "recovery_test_master_key"

    @Before
    @After
    fun clean() {
        context.deleteSharedPreferences(name)
        deleteMasterKey()
    }

    private fun deleteMasterKey() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(keyAlias)
    }

    private fun corruptKeySets() {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            .putString("__androidx_security_crypto_encrypted_prefs_key_keyset__", "AAAA")
            .putString("__androidx_security_crypto_encrypted_prefs_value_keyset__", "AAAA")
            .commit()
    }

    private fun open() = encryptedPreferences(context, name, keyAlias)

    private fun rawCreate() = EncryptedSharedPreferences.create(
        context,
        name,
        MasterKey.Builder(context, keyAlias).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    @Test
    fun a_lost_master_key_is_what_crashed_the_app() {
        open().edit().putString("k", "v").commit()
        deleteMasterKey()
        try {
            rawCreate()
            fail("raw create should not open a file whose master key is gone")
        } catch (expected: GeneralSecurityException) {
            // AEADBadTagException: what Application.onCreate died of.
        }
    }

    @Test
    fun a_lost_master_key_is_recovered_from() {
        open().edit().putString("k", "v").commit()
        deleteMasterKey()

        val recovered = open()

        assertNull("the old value is gone, not resurrected", recovered.getString("k", null))
        recovered.edit().putString("k", "again").commit()
        assertEquals("again", open().getString("k", null))
    }

    @Test
    fun a_corrupt_key_set_fails_as_io_not_security() {
        open().edit().putString("k", "v").commit()
        corruptKeySets()
        try {
            rawCreate()
            fail("raw create should not open a corrupt key set")
        } catch (expected: IOException) {
            // InvalidProtocolBufferException. The opener must catch this too.
        }
    }

    @Test
    fun a_corrupt_key_set_is_recovered_from() {
        open().edit().putString("k", "v").commit()
        corruptKeySets()

        val recovered = open()

        assertNull(recovered.getString("k", null))
        recovered.edit().putString("k", "again").commit()
        assertEquals("again", open().getString("k", null))
    }

    @Test
    fun a_healthy_file_is_left_alone() {
        open().edit().putString("k", "v").commit()
        assertEquals("v", open().getString("k", null))
    }
}
