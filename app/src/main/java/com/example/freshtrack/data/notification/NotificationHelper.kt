package com.example.freshtrack.data.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.freshtrack.FreshTrackApplication
import com.example.freshtrack.MainActivity
import com.example.freshtrack.R
import com.example.freshtrack.data.notification.NotificationHelper.sendExpiryNotification
import com.example.freshtrack.data.repository.ProductRepository
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Worker that checks for expiring products and sends notifications
 */
class ExpiryNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val productRepository: ProductRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            checkExpiringProducts()
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }

    private suspend fun checkExpiringProducts() {
        // Get products expiring in next 3 days
        val expiringProducts = productRepository.getExpiringProducts(daysThreshold = 3)

        if (expiringProducts.isNotEmpty()) {
            sendExpiryNotification(
                context = applicationContext,
                productCount = expiringProducts.size,
                productNames = expiringProducts.take(3).map { it.name }
            )
        }
    }
}

/**
 * Notification Manager for FreshTrack
 */
object NotificationHelper {

    private const val NOTIFICATION_ID_EXPIRY = 1001
    private const val NOTIFICATION_ID_DAILY = 1002
    private const val GROUP_KEY_EXPIRY = "com.example.freshtrack.EXPIRY_GROUP"

    /**
     * Send notification for expiring products
     */
    fun sendExpiryNotification(
        context: Context,
        productCount: Int,
        productNames: List<String>
    ) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // View Items action
        val viewIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "expiring")
        }
        val viewPendingIntent = PendingIntent.getActivity(
            context,
            1,
            viewIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = when {
            productCount == 1 -> "\uD83D\uDEA8 1 Product Expiring Soon"
            else -> "\uD83D\uDEA8 $productCount Products Expiring Soon"
        }

        val summaryText = when {
            productCount == 1 -> productNames.first()
            else -> "$productCount items need your attention"
        }

        // Build InboxStyle with individual product names
        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle(title)
            .setSummaryText("FreshTrack")

        productNames.take(5).forEach { name ->
            inboxStyle.addLine("\u2022 $name")
        }
        if (productCount > 5) {
            inboxStyle.addLine("...and ${productCount - 5} more")
        }

        val notification = NotificationCompat.Builder(
            context,
            FreshTrackApplication.CHANNEL_ID_EXPIRY_ALERTS
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(summaryText)
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .addAction(
                R.drawable.ic_notification,
                "View Items",
                viewPendingIntent
            )
            .setGroup(GROUP_KEY_EXPIRY)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_EXPIRY, notification)
    }

    /**
     * Send daily summary notification
     */
    fun sendDailySummaryNotification(
        context: Context,
        totalProducts: Int,
        expiringCount: Int
    ) {
        if (expiringCount == 0) return

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("\uD83D\uDCCA Daily Summary")
            .setSummaryText("FreshTrack")
            .addLine("\uD83D\uDCE6 $totalProducts products tracked")
            .addLine("\u26A0\uFE0F $expiringCount expiring soon")

        val notification = NotificationCompat.Builder(
            context,
            FreshTrackApplication.CHANNEL_ID_EXPIRY_ALERTS
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("\uD83D\uDCCA Daily Summary")
            .setContentText("$expiringCount of $totalProducts products expiring soon")
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_KEY_EXPIRY)
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_DAILY, notification)
    }
}

/**
 * Scheduler for periodic notification checks
 */
object NotificationScheduler {

    private const val WORK_NAME_EXPIRY_CHECK = "expiry_check_work"

    /**
     * Schedule daily expiry check at 9 AM
     */
    fun scheduleDailyExpiryCheck(context: Context) {
        val currentTime = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance().apply {
            timeInMillis = currentTime
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)

            // If 9 AM has passed today, schedule for tomorrow
            if (timeInMillis <= currentTime) {
                add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
        }

        val initialDelay = calendar.timeInMillis - currentTime

        val dailyWorkRequest = PeriodicWorkRequestBuilder<ExpiryNotificationWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME_EXPIRY_CHECK,
            ExistingPeriodicWorkPolicy.KEEP,
            dailyWorkRequest
        )
    }

    /**
     * Cancel scheduled notifications
     */
    fun cancelScheduledNotifications(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_EXPIRY_CHECK)
    }

    /**
     * Trigger immediate check (for testing)
     */
    fun triggerImmediateCheck(context: Context) {
        val immediateWorkRequest = OneTimeWorkRequestBuilder<ExpiryNotificationWorker>()
            .build()

        WorkManager.getInstance(context).enqueue(immediateWorkRequest)
    }
}