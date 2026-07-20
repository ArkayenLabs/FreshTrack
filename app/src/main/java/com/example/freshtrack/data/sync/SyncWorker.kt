package com.example.freshtrack.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.crashlytics.FirebaseCrashlytics
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Background sync. Requires a network, so WorkManager holds it until there is
 * one rather than the worker failing and burning a retry.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val syncer: ProductSyncer by inject()

    override suspend fun doWork(): Result = when (val result = syncer.sync()) {
        is SyncResult.Success -> Result.success()

        // Nothing to do and nothing wrong. Retrying would just burn battery
        // until the user signs in or subscribes.
        SyncResult.SkippedSignedOut,
        SyncResult.SkippedNotPremium -> Result.success()

        is SyncResult.Retryable -> Result.retry()

        is SyncResult.Failed -> {
            // Recorded rather than swallowed: a permanent sync failure is
            // invisible to the user, so without this it would never surface.
            FirebaseCrashlytics.getInstance().recordException(result.cause)
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "product_sync"
        private const val WORK_NAME_NOW = "product_sync_now"

        /**
         * Runs a sync as soon as there is a network. Used right after sign-in,
         * where waiting up to six hours for the periodic job would look like
         * the account simply has no data.
         */
        fun syncNow(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_NOW,
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
