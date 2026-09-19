package com.example.freshtrack.util

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase

object AnalyticsHelper {

    private var firebaseAnalytics: FirebaseAnalytics? = null

    fun init() {
        try {
            firebaseAnalytics = Firebase.analytics
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Turns collection on or off to match the user's consent.
     *
     * Must be called on every launch with the stored decision. The manifest
     * flags keep Firebase off until this runs, so a user who has not consented
     * never has anything collected, even in the moment before this executes.
     */
    fun applyConsent(granted: Boolean) {
        try {
            firebaseAnalytics?.setAnalyticsCollectionEnabled(granted)
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(granted)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * The one way an event leaves the app. Names and parameters are decided
     * in [UsageMetrics]; nothing identifying is ever passed here.
     */
    fun log(name: String, params: Bundle? = null) {
        firebaseAnalytics?.logEvent(name, params)
    }
}
