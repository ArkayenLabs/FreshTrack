package com.example.freshtrack.util

import android.app.Activity
import android.content.IntentSender
import android.util.Log
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability

class InAppUpdateManager(private val activity: Activity) {

    companion object {
        private const val TAG = "InAppUpdateManager"
        const val UPDATE_REQUEST_CODE = 1001
    }

    private val appUpdateManager: AppUpdateManager = AppUpdateManagerFactory.create(activity)
    private var updateListener: InstallStateUpdatedListener? = null
    private var onUpdateDownloaded: (() -> Unit)? = null

    fun checkForUpdates(
        onUpdateDownloaded: (() -> Unit)? = null,
        useImmediateUpdate: Boolean = false
    ) {
        this.onUpdateDownloaded = onUpdateDownloaded
        
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            handleUpdateInfo(appUpdateInfo, useImmediateUpdate)
        }.addOnFailureListener { exception ->
            Log.e(TAG, "Failed to check for updates: ${exception.message}")
        }
    }

    private fun handleUpdateInfo(appUpdateInfo: AppUpdateInfo, useImmediateUpdate: Boolean) {
        when (appUpdateInfo.updateAvailability()) {
            UpdateAvailability.UPDATE_AVAILABLE -> {
                val updateType = if (useImmediateUpdate) {
                    AppUpdateType.IMMEDIATE
                } else {
                    AppUpdateType.FLEXIBLE
                }
                
                if (appUpdateInfo.isUpdateTypeAllowed(updateType)) {
                    startUpdate(appUpdateInfo, updateType)
                }
            }
            UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                startUpdate(appUpdateInfo, AppUpdateType.IMMEDIATE)
            }
            else -> {
                Log.d(TAG, "No update available")
            }
        }
    }

    private fun startUpdate(appUpdateInfo: AppUpdateInfo, updateType: Int) {
        if (updateType == AppUpdateType.FLEXIBLE) {
            registerUpdateListener()
        }
        
        try {
            appUpdateManager.startUpdateFlowForResult(
                appUpdateInfo,
                activity,
                AppUpdateOptions.newBuilder(updateType).build(),
                UPDATE_REQUEST_CODE
            )
        } catch (e: IntentSender.SendIntentException) {
            Log.e(TAG, "Failed to start update flow: ${e.message}")
        }
    }

    private fun registerUpdateListener() {
        updateListener = InstallStateUpdatedListener { state ->
            when (state.installStatus()) {
                InstallStatus.DOWNLOADED -> {
                    onUpdateDownloaded?.invoke()
                }
                InstallStatus.INSTALLED -> {
                    unregisterUpdateListener()
                }
                else -> {}
            }
        }
        updateListener?.let { appUpdateManager.registerListener(it) }
    }

    private fun unregisterUpdateListener() {
        updateListener?.let { appUpdateManager.unregisterListener(it) }
        updateListener = null
    }

    fun completeUpdate() {
        appUpdateManager.completeUpdate()
    }

    fun checkForStalledUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { appUpdateInfo ->
            if (appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED) {
                onUpdateDownloaded?.invoke()
            }
            if (appUpdateInfo.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS) {
                startUpdate(appUpdateInfo, AppUpdateType.IMMEDIATE)
            }
        }
    }

    fun cleanup() {
        unregisterUpdateListener()
    }
}
