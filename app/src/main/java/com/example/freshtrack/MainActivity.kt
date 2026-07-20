package com.example.freshtrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.freshtrack.presentation.component.RequestNotificationPermissionSimple
import com.example.freshtrack.presentation.navigation.FreshTrackNavGraph
import com.example.freshtrack.presentation.theme.FreshTrackTheme
import com.example.freshtrack.util.InAppUpdateManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    
    private var inAppUpdateManager: InAppUpdateManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as FreshTrackApplication
        val wasReset = app.crashLoopDetector.wasAppReset()

        setContent {
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            var showResetDialog by remember { mutableStateOf(wasReset) }
            
            LaunchedEffect(Unit) {
                app.crashLoopDetector.onAppRunningStable()
            }
            
            inAppUpdateManager = remember {
                InAppUpdateManager(this@MainActivity).apply {
                    checkForUpdates(
                        onUpdateDownloaded = {
                            scope.launch {
                                val result = snackbarHostState.showSnackbar(
                                    message = "Update downloaded",
                                    actionLabel = "RESTART",
                                    duration = SnackbarDuration.Indefinite
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    completeUpdate()
                                }
                            }
                        }
                    )
                }
            }
            
            if (showResetDialog) {
                AlertDialog(
                    onDismissRequest = { showResetDialog = false },
                    title = { Text("App Reset") },
                    text = { 
                        Text("FreshTrack encountered repeated issues and has been reset. Your data has been cleared to restore functionality.")
                    },
                    confirmButton = {
                        TextButton(onClick = { showResetDialog = false }) {
                            Text("OK")
                        }
                    }
                )
            }
            
            FreshTrackTheme {
                RequestNotificationPermissionSimple()
                Scaffold(
                    snackbarHost = {
                        SnackbarHost(hostState = snackbarHostState) { data ->
                            Snackbar(
                                snackbarData = data,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                actionColor = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                ) { _ ->
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val navController = rememberNavController()
                        FreshTrackNavGraph(navController = navController)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        inAppUpdateManager?.checkForStalledUpdate()
    }

    override fun onDestroy() {
        super.onDestroy()
        inAppUpdateManager?.cleanup()
    }

    override fun onStop() {
        super.onStop()
        // The user has most likely just added or resolved something. Android's
        // own widget update period is clamped to 30 minutes and skipped while
        // dozing, so refreshing on the way out is what keeps it current.
        lifecycleScope.launch {
            com.example.freshtrack.widget.WidgetRefresher.refresh(this@MainActivity)
        }
    }
}
