package com.example.freshtrack.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

/**
 * Runs one [SyncRun] in the background.
 *
 * Only [SyncRun.Result.DEFERRED] asks WorkManager to retry: that is the one
 * outcome where trying again later might change the answer. A free account is
 * not an error and a signed-out device has nothing to do, so both succeed.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val syncRun: SyncRun by inject()

    override suspend fun doWork(): Result = when (syncRun.run()) {
        SyncRun.Result.DEFERRED -> Result.retry()
        else -> Result.success()
    }

    companion object {
        const val PERIODIC_WORK = "sync-periodic"
        const val ONE_SHOT_WORK = "sync-now"

        private val needsNetwork = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * A steady background cadence, for changes made while the app was
         * closed on another device. Six hours: a backup is not a chat.
         */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(needsNetwork)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        /**
         * A run as soon as the network allows. Called when the app goes to
         * the background — the natural moment for "what I just did" to go up
         * — and from the Settings card. REPLACE, so tapping twice is one run.
         */
        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(needsNetwork)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_SHOT_WORK, ExistingWorkPolicy.REPLACE, request
            )
        }
    }
}
