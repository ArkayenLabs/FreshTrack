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
import android.content.Intent
import com.example.freshtrack.presentation.navigation.LaunchTarget
import com.example.freshtrack.presentation.navigation.FreshTrackNavGraph
import com.example.freshtrack.presentation.theme.FreshTrackTheme
import com.example.freshtrack.util.InAppUpdateManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource

class MainActivity : ComponentActivity() {

    /**
     * Where a launch wants to land, when it wants somewhere other than
     * Today: a widget row opens its item, the widget's plus opens Add, a
     * notification opens what is expiring. Read from the intent on create
     * and again on a new intent (the activity is singleTop), consumed by the
     * nav graph once it has acted on it.
     */
    private var launchTarget by mutableStateOf<LaunchTarget?>(null)

    private fun launchTargetOf(intent: Intent?): LaunchTarget? {
        intent?.getStringExtra(EXTRA_OPEN_ITEM)?.let { return LaunchTarget.OpenItem(it) }
        return when (intent?.getStringExtra(EXTRA_NAVIGATE_TO)) {
            "add" -> LaunchTarget.AddItem
            "expiring" -> LaunchTarget.Expiring
            else -> null
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        launchTargetOf(intent)?.let { launchTarget = it }
    }
    
    private var inAppUpdateManager: InAppUpdateManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        launchTarget = launchTargetOf(intent)

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
                    title = { Text(stringResource(R.string.crash_reset_title)) },
                    text = { 
                        Text(stringResource(R.string.crash_reset_message))
                    },
                    confirmButton = {
                        TextButton(onClick = { showResetDialog = false }) {
                            Text(stringResource(R.string.action_ok))
                        }
                    }
                )
            }
            
            FreshTrackTheme {
                // This Scaffold exists only to host the snackbar: it has no top or
                // bottom bar, and every destination in the nav graph brings its own
                // Scaffold. Consuming the content padding here would apply the
                // system insets a second time and push every screen down.
                @Suppress("UnusedMaterial3ScaffoldPaddingParameter")
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
                        FreshTrackNavGraph(
                            navController = navController,
                            launchTarget = launchTarget,
                            onLaunchTargetConsumed = { launchTarget = null }
                        )
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

    companion object {
        const val EXTRA_OPEN_ITEM = "open_item"
        const val EXTRA_NAVIGATE_TO = "navigate_to"
    }
}
