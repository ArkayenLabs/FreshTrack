package com.example.freshtrack.presentation.screen.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import com.example.freshtrack.data.export.CsvExporter
import com.example.freshtrack.data.repository.ItemRepository
import com.example.freshtrack.presentation.viewmodel.AuthViewModel
import com.example.freshtrack.presentation.viewmodel.SettingsViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import androidx.compose.ui.res.stringResource
import com.example.freshtrack.R
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.freshtrack.presentation.theme.pressable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    onNavigateToHistory: () -> Unit = {},
    onSignOut: () -> Unit = {},
    viewModel: SettingsViewModel = koinViewModel(),
    authViewModel: AuthViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var importSummary by remember {
        mutableStateOf<com.example.freshtrack.domain.model.ImportSummary?>(null)
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val itemRepository: ItemRepository = koinInject()

    // Resolved here because the places that show them — coroutines, activity
    // result callbacks, catch blocks — are not composition and cannot read
    // resources themselves.
    val fileEmptyMessage = stringResource(R.string.settings_file_empty)
    val exportEmptyMessage = stringResource(R.string.settings_export_empty)
    val exportChooserTitle = stringResource(R.string.settings_export_chooser)
    val exportFailedMessage = stringResource(R.string.settings_export_failed)
    val supportChooserTitle = stringResource(R.string.settings_help_chooser)
    val noEmailAppMessage = stringResource(R.string.settings_no_email_app)
    val accountDeletedMessage = stringResource(R.string.settings_delete_done)
    // The word someone must type to confirm deletion. A resource so it can
    // be translated, since asking for an English word in a German app is a
    // trap rather than a safeguard.
    val deleteKeyword = stringResource(R.string.settings_delete_keyword)
    val reauthNeededMessage = stringResource(R.string.settings_delete_reauth)

    val consentPreferences: com.example.freshtrack.data.preferences.ConsentPreferences = koinInject()
    var analyticsConsent by remember { mutableStateOf(consentPreferences.isAnalyticsGranted()) }

    val accountDeleter: com.example.freshtrack.data.account.AccountDeleter = koinInject()
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deleteConfirmText by remember { mutableStateOf("") }
    var isDeletingAccount by remember { mutableStateOf(false) }

    // The backup card reports what is actually true rather than what would
    // be reassuring: how many changes are waiting, how many could not be
    // sent, and why nothing is going up if nothing is. The last-run facts
    // live in preferences, re-read whenever the sync work changes state.
    val pendingChanges by itemRepository.observePendingSyncCount()
        .collectAsState(initial = 0)
    val stuckChanges by itemRepository.observeStuckSyncCount()
        .collectAsState(initial = 0)
    val syncState: com.example.freshtrack.data.sync.SyncState = koinInject()
    val syncWork by androidx.work.WorkManager.getInstance(context)
        .getWorkInfosForUniqueWorkFlow(com.example.freshtrack.data.sync.SyncWorker.ONE_SHOT_WORK)
        .collectAsState(initial = emptyList())
    val lastRun = remember(syncWork) { syncState.lastRun() }
    val lastSuccessAt = remember(syncWork) { syncState.lastSuccessAt() }
    val isSignedIn = FirebaseAuth.getInstance().currentUser != null

    // OpenDocument rather than GetContent: it returns a persistable URI and lets
    // the user pick from any provider, including Drive.
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImporting = true
        scope.launch {
            try {
                val text = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }
                if (text.isNullOrBlank()) {
                    Toast.makeText(context, fileEmptyMessage, Toast.LENGTH_SHORT).show()
                } else {
                    val parsed = com.example.freshtrack.data.export.CsvImporter.parse(text)
                    val summary = itemRepository.import(parsed.products)
                    importSummary = summary.copy(failedRows = parsed.errors.size)
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isImporting = false
            }
        }
    }

    // Profile edit state
    var showEditNameDialog by remember { mutableStateOf(false) }
    var editNameValue by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ─── User Profile Card ─────────────────────────────────────────────────
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Avatar circle with initial
                        val displayName = currentUser.displayName?.takeIf { it.isNotBlank() }
                        val email = currentUser.email ?: ""
                        val initial = displayName?.firstOrNull()?.uppercaseChar()
                            ?: email.firstOrNull()?.uppercaseChar() ?: '?'
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial.toString(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName ?: stringResource(R.string.settings_add_name),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (displayName != null)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                            if (email.isNotBlank()) {
                                Text(
                                    text = email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                editNameValue = currentUser.displayName ?: ""
                                showEditNameDialog = true
                            }
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = stringResource(R.string.settings_edit_name),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            } else {
                // Guest mode — nudge to sign in for cloud features
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.settings_guest_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                stringResource(R.string.settings_guest_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        TextButton(onClick = onSignOut, shape = MaterialTheme.shapes.small) {
                            Text(stringResource(R.string.settings_sign_in), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Notifications Section — removed (Daily Reminder + Advance Notice)
            // Notifications are always-on system-level; no per-user toggle needed.

            // Data & Storage Section
            SettingsSection(
                title = stringResource(R.string.settings_section_data),
                icon = Icons.Outlined.Storage
            ) {
                SettingsItemCard(
                    icon = Icons.Outlined.History,
                    title = stringResource(R.string.history_title),
                    description = stringResource(R.string.settings_history_body),
                    onClick = onNavigateToHistory
                )
                SettingsItemCard(
                    icon = Icons.Outlined.Upload,
                    title = if (isImporting) stringResource(R.string.settings_importing) else stringResource(R.string.settings_import),
                    description = stringResource(R.string.settings_import_body),
                    onClick = {
                        if (!isImporting) {
                            // Some providers label CSV as text/comma-separated-values
                            // or octet-stream, so accept a wider set than text/csv.
                            importLauncher.launch(
                                arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/octet-stream")
                            )
                        }
                    }
                )
                SettingsItemCard(
                    icon = Icons.Outlined.Download,
                    title = if (isExporting) stringResource(R.string.settings_exporting) else stringResource(R.string.settings_export),
                    description = stringResource(R.string.settings_export_body),
                    onClick = {
                        if (!isExporting) {
                            isExporting = true
                            scope.launch {
                                try {
                                    val products = itemRepository.observeActiveItems().first()
                                    if (products.isEmpty()) {
                                        Toast.makeText(context, exportEmptyMessage, Toast.LENGTH_SHORT).show()
                                    } else {
                                        val shareIntent = CsvExporter.exportToCSV(context, products)
                                        if (shareIntent != null) {
                                            context.startActivity(Intent.createChooser(shareIntent, exportChooserTitle))
                                        } else {
                                            Toast.makeText(context, exportFailedMessage, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isExporting = false
                                }
                            }
                        }
                    }
                )

                SettingsItemCard(
                    icon = if (isSignedIn) Icons.Outlined.Cloud else Icons.Outlined.CloudOff,
                    title = stringResource(R.string.settings_sync),
                    description = describeSync(
                        isSignedIn = isSignedIn,
                        lastRun = lastRun,
                        lastSuccessAt = lastSuccessAt,
                        pending = pendingChanges,
                        stuck = stuckChanges
                    ),
                    enabled = isSignedIn,
                    onClick = { com.example.freshtrack.data.sync.SyncWorker.syncNow(context) }
                )
            }

            // Privacy Section
            SettingsSection(
                title = stringResource(R.string.settings_section_privacy),
                icon = Icons.Outlined.Shield
            ) {
                SettingsSwitchCard(
                    icon = Icons.Outlined.Analytics,
                    title = stringResource(R.string.settings_analytics),
                    description = stringResource(R.string.settings_analytics_body),
                    checked = analyticsConsent,
                    onCheckedChange = { granted ->
                        analyticsConsent = granted
                        consentPreferences.setAnalyticsConsent(granted)
                        com.example.freshtrack.util.AnalyticsHelper.applyConsent(granted)
                    }
                )
            }

            // About Section
            SettingsSection(
                title = stringResource(R.string.settings_section_about),
                icon = Icons.Outlined.Info
            ) {
                SettingsItemCard(
                    icon = Icons.Outlined.PrivacyTip,
                    title = stringResource(R.string.settings_privacy_policy),
                    description = stringResource(R.string.settings_privacy_policy_body),
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.arkayenlabs.com/privacy/freshtrack"))
                        context.startActivity(intent)
                    }
                )

                SettingsItemCard(
                    icon = Icons.Outlined.Star,
                    title = stringResource(R.string.settings_rate),
                    description = stringResource(R.string.settings_rate_body),
                    onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"))
                            context.startActivity(intent)
                        }
                    }
                )

                SettingsItemCard(
                    icon = Icons.Outlined.HelpOutline,
                    title = stringResource(R.string.settings_help),
                    description = stringResource(R.string.settings_help_body),
                    onClick = {
                        val deviceInfo = "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\nAndroid: ${android.os.Build.VERSION.RELEASE}\nApp Version: 1.1.0"
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("hello@arkayenlabs.com"))
                            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.support_subject))
                            putExtra(Intent.EXTRA_TEXT, "\n\n---\n$deviceInfo")
                        }
                        try {
                            context.startActivity(Intent.createChooser(emailIntent, supportChooserTitle))
                        } catch (e: Exception) {
                            Toast.makeText(context, noEmailAppMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Sign Out Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                var showSignOutDialog by remember { mutableStateOf(false) }

                if (showSignOutDialog) {
                    AlertDialog(
                        onDismissRequest = { showSignOutDialog = false },
                        title = { Text(stringResource(R.string.settings_sign_out)) },
                        text = { Text(stringResource(R.string.settings_sign_out_confirm)) },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showSignOutDialog = false
                                    scope.launch {
                                        authViewModel.signOut()
                                        onSignOut()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) { Text(stringResource(R.string.settings_sign_out)) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSignOutDialog = false }) { Text(stringResource(R.string.action_cancel)) }
                        }
                    )
                }

                OutlinedButton(
                    onClick = { showSignOutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                ) {
                    Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_sign_out))
                }

                // Deliberately quiet: a destructive, irreversible action should
                // be findable but never sit where Sign Out is expected.
                if (FirebaseAuth.getInstance().currentUser != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = {
                            deleteConfirmText = ""
                            showDeleteAccountDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.settings_delete_account),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // App Info Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_licenses),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    modifier = Modifier.clickable { onNavigateToLicenses() }
                )
            }
        }
    }

    // Name Edit Dialog
    if (showEditNameDialog) {
        var nameInput by remember { mutableStateOf(editNameValue) }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text(stringResource(R.string.settings_edit_name)) },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text(stringResource(R.string.settings_display_name)) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedName = nameInput.trim()
                        val user = FirebaseAuth.getInstance().currentUser
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(trimmedName)
                            .build()
                        user?.updateProfile(profileUpdates)
                            ?.addOnCompleteListener { /* profile updated */ }
                        showEditNameDialog = false
                    },
                    shape = MaterialTheme.shapes.medium
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }, shape = MaterialTheme.shapes.medium) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            shape = MaterialTheme.shapes.large
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDeletingAccount) showDeleteAccountDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.settings_delete_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Naming what goes, rather than a vague warning, so the
                    // decision is made with the facts in front of them.
                    Text(stringResource(R.string.settings_delete_intro))
                    Text(
                        stringResource(R.string.settings_delete_bullets),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        stringResource(R.string.settings_delete_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it },
                        singleLine = true,
                        enabled = !isDeletingAccount,
                        label = { Text(stringResource(R.string.settings_delete_prompt)) }
                    )
                }
            },
            confirmButton = {
                Button(
                    // Typing is friction on purpose: a single mis-tap should not
                    // be able to destroy someone's account.
                    enabled = deleteConfirmText.trim() == deleteKeyword &&
                        !isDeletingAccount,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    onClick = {
                        isDeletingAccount = true
                        scope.launch {
                            when (val result = accountDeleter.deleteAccount()) {
                                com.example.freshtrack.data.account.AccountDeleter.Result.Success -> {
                                    showDeleteAccountDialog = false
                                    Toast.makeText(
                                        context,
                                        accountDeletedMessage,
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onSignOut()
                                }

                                com.example.freshtrack.data.account.AccountDeleter.Result.NeedsRecentLogin -> {
                                    showDeleteAccountDialog = false
                                    Toast.makeText(
                                        context,
                                        reauthNeededMessage,
                                        Toast.LENGTH_LONG
                                    ).show()
                                    authViewModel.signOut()
                                    onSignOut()
                                }

                                is com.example.freshtrack.data.account.AccountDeleter.Result.Failed -> {
                                    Toast.makeText(
                                        context,
                                        "Could not delete account: ${result.cause.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                            isDeletingAccount = false
                        }
                    }
                ) {
                    Text(if (isDeletingAccount) stringResource(R.string.settings_deleting) else stringResource(R.string.settings_delete_forever))
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeletingAccount,
                    onClick = { showDeleteAccountDialog = false }
                ) { Text(stringResource(R.string.action_cancel)) }
            },
            shape = MaterialTheme.shapes.large
        )
    }

    // A summary rather than a toast: skipped duplicates need explaining, or the
    // user assumes the import silently lost their data.
    importSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { importSummary = null },
            icon = { Icon(Icons.Outlined.Upload, contentDescription = null) },
            title = { Text(stringResource(R.string.settings_import_complete)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(stringResource(R.string.settings_import_added, summary.imported))
                    if (summary.skippedDuplicates > 0) {
                        Text(stringResource(R.string.settings_import_duplicates, summary.skippedDuplicates))
                    }
                    if (summary.failedRows > 0) {
                        Text(stringResource(R.string.settings_import_failed_rows, summary.failedRows))
                    }
                    if (summary.imported == 0 && summary.skippedDuplicates > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.settings_import_nothing_new),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { importSummary = null }) { Text(stringResource(R.string.action_done)) }
            }
        )
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsItemCard(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val press = remember { MutableInteractionSource() }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(press)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, interactionSource = press, indication = LocalIndication.current) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (enabled)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            if (enabled) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchCard(
    icon: ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (checked)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun AdvanceNoticeDaysDialog(
    currentDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedDays by remember { mutableStateOf(currentDays) }
    val dayOptions = listOf(1, 2, 3, 5, 7, 10, 14)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                "Advance Notice",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Get notified before products expire",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                dayOptions.forEach { days ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { selectedDays = days }
                            .background(
                                if (selectedDays == days)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surface
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$days ${if (days == 1) "day" else "days"} before",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (selectedDays == days)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface
                        )

                        if (selectedDays == days) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedDays) },
                shape = MaterialTheme.shapes.medium
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = MaterialTheme.shapes.medium
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        shape = MaterialTheme.shapes.large,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}
/**
 * Wording for the Backup & Sync card.
 *
 * Deliberately plain about the cases that are not success. A backup that
 * silently is not happening is worse than no backup, because the user stops
 * worrying about it. So: why nothing is going up, then how much is waiting,
 * then how much could not be sent — each only when it is true.
 */
@Composable
private fun describeSync(
    isSignedIn: Boolean,
    lastRun: com.example.freshtrack.data.sync.SyncRun.Result?,
    lastSuccessAt: Long,
    pending: Int,
    stuck: Int
): String {
    val status = when {
        !isSignedIn -> stringResource(R.string.settings_sync_signed_out)
        lastRun == com.example.freshtrack.data.sync.SyncRun.Result.NOT_ENTITLED ->
            stringResource(R.string.settings_sync_needs_premium)
        lastRun == com.example.freshtrack.data.sync.SyncRun.Result.DEFERRED && lastSuccessAt <= 0L ->
            stringResource(R.string.settings_sync_unreachable)
        lastSuccessAt <= 0L -> stringResource(R.string.settings_sync_never)
        else -> describeLastSync(lastSuccessAt)
    }
    val waiting = if (pending > 0 && isSignedIn) {
        " " + pluralStringResource(R.plurals.settings_sync_pending, pending, pending)
    } else ""
    val failed = if (stuck > 0) {
        " " + pluralStringResource(R.plurals.settings_sync_stuck, stuck, stuck)
    } else ""
    return status + waiting + failed
}

/** "Backed up 3 hours ago." */
@Composable
private fun describeLastSync(lastSuccessAt: Long): String {
    val elapsed = System.currentTimeMillis() - lastSuccessAt
    val minutes = (elapsed / 60_000).toInt()
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> stringResource(R.string.settings_sync_just_now)
        minutes < 60 -> pluralStringResource(R.plurals.settings_sync_minutes_ago, minutes, minutes)
        hours < 24 -> pluralStringResource(R.plurals.settings_sync_hours_ago, hours, hours)
        else -> pluralStringResource(R.plurals.settings_sync_days_ago, days, days)
    }
}
