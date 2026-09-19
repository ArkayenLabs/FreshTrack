package com.example.freshtrack.util

import android.app.Activity
import com.example.freshtrack.data.preferences.OnboardingPreferences
import com.example.freshtrack.data.repository.ItemRepository
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * Asks for a store rating only after the thing the listing promises has
 * just happened: something at risk was used. Gated locally, hard, before
 * Play's own quota is consulted: the app has been around a few days, a few
 * things have been resolved, and it has not asked in two months. Play may
 * still decide not to show the sheet; that is fine, the gate is what keeps
 * this from becoming a nag.
 */
class ReviewPrompter(
    private val preferences: OnboardingPreferences,
    private val itemRepository: ItemRepository,
    private val metrics: UsageMetrics,
    private val clock: AppClock = AppClock.System
) {
    suspend fun shouldAsk(): Boolean {
        val now = clock.nowMillis()
        val installedFor = now - preferences.firstOpenedAt(now)
        if (installedFor < MIN_DAYS_INSTALLED * DAY_MS) return false
        if (now - preferences.lastReviewPromptAt() < MIN_DAYS_BETWEEN * DAY_MS) return false
        val resolved = runCatching { itemRepository.observeImpact().first().totalResolved }.getOrDefault(0)
        return resolved >= MIN_RESOLVED
    }

    /** Records the ask first, so a crash mid-sheet still counts as asked. */
    suspend fun ask(activity: Activity) {
        preferences.setLastReviewPromptAt(clock.nowMillis())
        metrics.reviewPrompted()
        runCatching {
            val manager = ReviewManagerFactory.create(activity)
            val info = manager.requestReviewFlow().await()
            manager.launchReviewFlow(activity, info).await()
        }
    }

    companion object {
        const val MIN_DAYS_INSTALLED = 3
        const val MIN_RESOLVED = 3
        const val MIN_DAYS_BETWEEN = 60
        private const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
