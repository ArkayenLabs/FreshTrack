package com.example.freshtrack.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

/**
 * Single place that pushes fresh data into the home-screen widget.
 *
 * Android clamps a widget's own update period to 30 minutes and skips it while
 * dozing, so relying on that alone leaves stale contents. Callers refresh at the
 * moments that matter: leaving the app, and the daily expiry check.
 *
 * Failures are swallowed — a widget that cannot refresh must never take down
 * the caller.
 */
object WidgetRefresher {
    suspend fun refresh(context: Context) {
        runCatching { FreshTrackWidget().updateAll(context) }
    }
}
