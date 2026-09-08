package com.example.freshtrack

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.example.freshtrack.di.appModules
import org.koin.android.ext.koin.androidContext
import com.example.freshtrack.data.notification.NotificationScheduler
import com.example.freshtrack.util.CrashLoopDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class FreshTrackApplication : Application() {

    lateinit var crashLoopDetector: CrashLoopDetector
        private set

    override fun onCreate() {
        super.onCreate()

        crashLoopDetector = CrashLoopDetector(this)
        crashLoopDetector.onAppStarting()

        com.example.freshtrack.util.AnalyticsHelper.init()
        // Apply the stored consent immediately. Off by default via the manifest,
        // so a user who has never consented has nothing collected.
        com.example.freshtrack.util.AnalyticsHelper.applyConsent(
            com.example.freshtrack.data.preferences.ConsentPreferences(this).isAnalyticsGranted()
        )

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                crashLoopDetector.onAppExitCleanly()
            }
        })

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@FreshTrackApplication)
            modules(appModules)
        }

        createNotificationChannels()
        NotificationScheduler.scheduleDailyExpiryCheck(this)
        NotificationScheduler.scheduleWeeklySummary(this)

        claimLocalDataForSignedInUser()
    }

    /**
     * Adopts anything created before sign-in into the account's own kitchen.
     *
     * Runs on every start because it is cheap — a COUNT that returns zero in the
     * common case — and it covers the guest-then-sign-up path without needing a
     * separate hook at the point of sign-in.
     */
    private fun claimLocalDataForSignedInUser() {
        val repository: com.example.freshtrack.data.repository.ItemRepository =
            org.koin.java.KoinJavaComponent.get(
                com.example.freshtrack.data.repository.ItemRepository::class.java
            )
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { repository.claimLocalData() }
                .onFailure {
                    com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                        .recordException(it)
                }
        }
    }

    private fun createNotificationChannels() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID_EXPIRY_ALERTS,
                "Expiry Alerts",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for products expiring soon"
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = getSystemService(NOTIFICATION_SERVICE)
                    as android.app.NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID_EXPIRY_ALERTS = "expiry_alerts"
    }
}

