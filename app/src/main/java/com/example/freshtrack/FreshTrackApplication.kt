package com.example.freshtrack

import android.app.Application
import com.example.freshtrack.di.appModules
import org.koin.android.ext.koin.androidContext
import com.example.freshtrack.data.notification.NotificationScheduler
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * Main Application class for FreshTrack
 * Initializes Koin dependency injection and other app-wide configurations
 */
class FreshTrackApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Koin for Dependency Injection
        startKoin {
            // Enable Koin logging for debugging (remove in production)
            androidLogger(Level.ERROR)

            // Provide Android context to Koin
            androidContext(this@FreshTrackApplication)

            // Load all Koin modules
            modules(appModules)
        }

        // Initialize notification channels
        createNotificationChannels()

        // Schedule daily expiry checks
        NotificationScheduler.scheduleDailyExpiryCheck(this)

    }

    /**
     * Create notification channels for Android O and above
     * Required for displaying notifications
     */
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