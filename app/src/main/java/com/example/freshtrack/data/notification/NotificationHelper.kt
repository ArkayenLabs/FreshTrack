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
        // Already-expired items were previously never notified: the expiring
        // query filters to expiryDate >= now, so anything past its date fell
        // silently out of every alert. Those are the ones that matter most.
        val expired = productRepository.getExpiredProducts().first()
        val expiringSoon = productRepository.getExpiringProducts(daysThreshold = 3)

        val all = (expired + expiringSoon).distinctBy { it.id }
        if (all.isNotEmpty()) {
            sendExpiryNotification(applicationContext, all)
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
     * Send notification for expiring products.
     *
     * Copy, priority and available actions all follow how urgent the worst item
     * is, so an expired item does not read the same as one due next week.
     */
    fun sendExpiryNotification(
        context: Context,
        products: List<com.example.freshtrack.domain.model.Product>
    ) {
        val content = ExpiryNotificationContent.build(products) ?: return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("navigate_to", "expiring")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(
            context,
            FreshTrackApplication.CHANNEL_ID_EXPIRY_ALERTS
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(content.title)
            .setContentText(content.summary)
            .setPriority(
                when (content.urgency) {
                    ExpiryNotificationContent.Urgency.EXPIRED,
                    ExpiryNotificationContent.Urgency.TODAY ->
                        NotificationCompat.PRIORITY_HIGH
                    else -> NotificationCompat.PRIORITY_DEFAULT
                }
            )
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_KEY_EXPIRY)
            .setAutoCancel(true)

        // A list only helps when there is more than one item; for a single
        // product the title already says everything.
        if (content.lines.isNotEmpty()) {
            val inbox = NotificationCompat.InboxStyle()
                .setBigContentTitle(content.title)
                .setSummaryText("FreshTrack")
            content.lines.forEach { inbox.addLine(it) }
            builder.setStyle(inbox)
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(content.summary))
        }

        // Resolving a single item is the common response, so offer it here
        // rather than making the user open the app to do it.
        content.singleProductId?.let { productId ->
            val markUsedIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = NotificationActionReceiver.ACTION_MARK_USED
                putExtra(NotificationActionReceiver.EXTRA_PRODUCT_ID, productId)
                putExtra(NotificationActionReceiver.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID_EXPIRY)
            }
            builder.addAction(
                R.drawable.ic_notification,
                "Mark as used",
                PendingIntent.getBroadcast(
                    context,
                    productId.hashCode(),
                    markUsedIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }

        builder.addAction(R.drawable.ic_notification, "View items", pendingIntent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_EXPIRY, builder.build())
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
            .setBigContentTitle("Daily Summary")
            .setSummaryText("FreshTrack")
            .addLine("\u2022 $totalProducts products tracked")
            .addLine("\u2022 $expiringCount expiring soon")

        val notification = NotificationCompat.Builder(
            context,
            FreshTrackApplication.CHANNEL_ID_EXPIRY_ALERTS
        )
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Daily Summary")
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
    private const val WORK_NAME_WEEKLY_SUMMARY = "weekly_summary_work"

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
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_WEEKLY_SUMMARY)
    }

    /**
     * Schedule Weekly Summary
     */
    fun scheduleWeeklySummary(context: Context) {
        val weeklyWorkRequest = PeriodicWorkRequestBuilder<WeeklySummaryWorker>(
            repeatInterval = 7,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME_WEEKLY_SUMMARY,
            ExistingPeriodicWorkPolicy.KEEP,
            weeklyWorkRequest
        )
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