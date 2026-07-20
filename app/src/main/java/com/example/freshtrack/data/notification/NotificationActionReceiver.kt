package com.example.freshtrack.data.notification

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.freshtrack.data.repository.ProductRepository
import com.example.freshtrack.util.AnalyticsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Handles actions tapped directly on a notification.
 *
 * The point is that the most common response to "your milk expires today" is
 * "yes, I used it" — which should not require opening the app, finding the item
 * and tapping through to resolve it.
 */
class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {

    private val productRepository: ProductRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val productId = intent.getStringExtra(EXTRA_PRODUCT_ID) ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        when (intent.action) {
            ACTION_MARK_USED -> {
                // The broadcast would otherwise be killed as soon as onReceive
                // returns, before the database write completes.
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        productRepository.markAsConsumed(productId)
                        AnalyticsHelper.logItemConsumed("notification", false)
                        if (notificationId != -1) {
                            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
                                as NotificationManager
                            manager.cancel(notificationId)
                        }
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_MARK_USED = "com.example.freshtrack.action.MARK_USED"
        const val EXTRA_PRODUCT_ID = "product_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}
