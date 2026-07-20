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
        com.example.freshtrack.data.sync.SyncWorker.schedule(this)

        claimGuestDataForSignedInUser()
    }

    /**
     * Adopts any guest-owned rows for the signed-in account.
     *
     * This is not optional. The 5→6 migration backfills every pre-existing row to
     * 'guest', so a user who was already signed in when they updated would open
     * the app to an empty inventory until those rows are claimed. Runs on every
     * start because it is cheap (a COUNT that returns 0 in the common case) and
     * also covers the guest-then-sign-up path.
     */
    private fun claimGuestDataForSignedInUser() {
        val repository: com.example.freshtrack.data.repository.ProductRepository =
            org.koin.java.KoinJavaComponent.get(
                com.example.freshtrack.data.repository.ProductRepository::class.java
            )
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { repository.claimGuestData() }
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

