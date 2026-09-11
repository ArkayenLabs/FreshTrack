package com.example.freshtrack.util

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * What the crash-loop reset is allowed to destroy.
 *
 * The answer is: caches and recoverable preferences, and nothing else. This app
 * is offline-first and most of its users are guests with no cloud copy, so the
 * database is the one thing a recovery must never take — losing a kitchen and
 * its impact ledger is worse for the user than the crash the reset is trying to
 * escape. An earlier version deleted the whole data directory apart from
 * shared_prefs, which is the exact inverse.
 */
@RunWith(AndroidJUnit4::class)
class CrashLoopDetectorTest {

    private lateinit var context: Context
    private lateinit var detector: CrashLoopDetector

    private fun detectorPrefs() =
        context.getSharedPreferences("crash_loop_detector", Context.MODE_PRIVATE)

    /** Drives three consecutive starts that never reported a clean exit. */
    private fun triggerReset() {
        repeat(3) {
            detectorPrefs().edit().putBoolean("app_is_running", true).commit()
            detector.onAppStarting()
        }
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        detector = CrashLoopDetector(context)
        detectorPrefs().edit().clear().commit()
    }

    @Test
    fun theResetKeepsTheDatabase() {
        // A stand-in rather than the real goodbefore_database: the app under
        // test has that one open, and writing over it here would corrupt it.
        // What is being proved is that databases/ is left alone at all.
        val databases = File(context.filesDir.parentFile, "databases").apply { mkdirs() }
        val db = File(databases, "crash_loop_probe.db").apply { writeText("pantry") }
        try {
            triggerReset()

            assertTrue("the crash-loop reset deleted the databases directory", db.exists())
            assertEquals("pantry", db.readText())
        } finally {
            db.delete()
        }
    }

    @Test
    fun theResetKeepsFilesTheAppOwns() {
        val owned = File(context.filesDir, "owned.txt").apply { writeText("keep me") }

        triggerReset()

        assertTrue("the crash-loop reset deleted files/", owned.exists())
    }

    @Test
    fun theResetClearsTheCacheButKeepsTheDirectory() {
        val cached = File(context.cacheDir, "stale.tmp").apply { writeText("junk") }

        triggerReset()

        assertFalse(cached.exists())
        assertTrue("cacheDir itself must survive", context.cacheDir.isDirectory)
    }

    @Test
    fun theResetClearsOnboardingAndConsentPreferences() {
        // A probe key rather than a real one: these files are written through
        // EncryptedSharedPreferences by the app, and a plaintext entry left
        // behind in one of them would not decrypt. The reset removes it, which
        // is what this asserts.
        val probe = "crash_loop_test_probe"
        context.getSharedPreferences("freshtrack_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean(probe, true).commit()
        context.getSharedPreferences("freshtrack_consent_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean(probe, true).commit()

        triggerReset()

        assertFalse(
            context.getSharedPreferences("freshtrack_prefs", Context.MODE_PRIVATE).contains(probe)
        )
        assertFalse(
            context.getSharedPreferences("freshtrack_consent_prefs", Context.MODE_PRIVATE)
                .contains(probe)
        )
    }

    @Test
    fun theResetIsReportedOnceAndThenForgotten() {
        triggerReset()

        assertTrue(detector.wasAppReset())
        assertFalse("the dialog must not reappear on every later start", detector.wasAppReset())
    }

    @Test
    fun aCleanExitIsNotCountedAsACrash() {
        repeat(5) {
            detector.onAppStarting()
            detector.onAppExitCleanly()
        }

        assertFalse(detector.wasAppReset())
    }
}
