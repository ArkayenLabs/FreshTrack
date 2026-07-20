package com.example.freshtrack.data.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.freshtrack.FreshTrackApplication
import com.example.freshtrack.MainActivity
import com.example.freshtrack.R
import com.example.freshtrack.data.repository.ProductRepository
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WeeklySummaryWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val productRepository: ProductRepository by inject()

    override suspend fun doWork(): Result {
        val stats = productRepository.getImpactStats().first()
        val saved = stats.itemsSaved
        val wasted = stats.itemsWasted
        val wasteFreeDays = stats.wasteFreeDays

        if (!stats.hasHistory) return Result.success()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            2,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val inboxStyle = NotificationCompat.InboxStyle()
            .setBigContentTitle("Weekly FreshTrack Summary")
            .addLine("Items Used: $saved")
            .addLine("Items Wasted: $wasted")
            .addLine("Waste-Free Days: $wasteFreeDays")

        val notification = NotificationCompat.Builder(context, FreshTrackApplication.CHANNEL_ID_EXPIRY_ALERTS)
            .setSmallIcon(R.drawable.ic_notification) // Ensure this exists, using default from other notifications
            .setContentTitle("Weekly FreshTrack Summary")
            .setContentText("You used $saved items and wasted $wasted so far.")
            .setStyle(inboxStyle)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(1003, notification)

        // Reset weekly counters if desired, but here we just keep them running or add a mechanism to reset.
        // For simplicity, we just keep a running total.

        return Result.success()
    }
}
