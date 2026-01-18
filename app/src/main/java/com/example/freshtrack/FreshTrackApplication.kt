package com.example.freshtrack

import android.app.Application
import com.example.freshtrack.di.appModules
import org.koin.android.ext.koin.androidContext
import com.example.freshtrack.data.notification.NotificationScheduler
import com.example.freshtrack.util.CrashLoopDetector
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

        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@FreshTrackApplication)
            modules(appModules)
        }

        createNotificationChannels()
        NotificationScheduler.scheduleDailyExpiryCheck(this)
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
