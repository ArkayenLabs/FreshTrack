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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.freshtrack.data.export.CsvExporter
import com.example.freshtrack.data.repository.ProductRepository
import com.example.freshtrack.presentation.viewmodel.AuthViewModel
import com.example.freshtrack.presentation.viewmodel.SettingsViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    onNavigateToHistory: () -> Unit = {},
    onNavigateToImpact: () -> Unit = {},
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
    val productRepository: ProductRepository = koinInject()
    val productSyncer: com.example.freshtrack.data.sync.ProductSyncer = koinInject()
    val syncPreferences: com.example.freshtrack.data.preferences.SyncPreferences = koinInject()

    val accountDeleter: com.example.freshtrack.data.account.AccountDeleter = koinInject()
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deleteConfirmText by remember { mutableStateOf("") }
    var isDeletingAccount by remember { mutableStateOf(false) }

    var isSyncing by remember { mutableStateOf(false) }
    // Seeded from the stored timestamp so the card says something truthful
    // before any sync is attempted in this session.
    var syncStatus by remember {
        mutableStateOf(
            if (FirebaseAuth.getInstance().currentUser == null) {
                "Sign in to back up your items"
            } else {
                describeLastSync(syncPreferences.lastSuccessAt())
            }
        )
    }

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
                    Toast.makeText(context, "That file is empty", Toast.LENGTH_SHORT).show()
                } else {
                    val parsed = com.example.freshtrack.data.export.CsvImporter.parse(text)
                    val summary = productRepository.importProducts(parsed.products)
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
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
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
                    shape = RoundedCornerShape(20.dp),
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
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName ?: "Add your name",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
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
                                contentDescription = "Edit name",
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
                    shape = RoundedCornerShape(20.dp),
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
                                "Using as Guest",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "Sign in to unlock backup & sync",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        TextButton(onClick = onSignOut, shape = RoundedCornerShape(10.dp)) {
                            Text("Sign In", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            // Notifications Section — removed (Daily Reminder + Advance Notice)
            // Notifications are always-on system-level; no per-user toggle needed.

            // Data & Storage Section
            SettingsSection(
                title = "Data & Storage",
                icon = Icons.Outlined.Storage
            ) {
                SettingsItemCard(
                    icon = Icons.Outlined.Insights,
                    title = "Your Impact",
                    description = "Waste-free days and used vs. wasted",
                    onClick = onNavigateToImpact
                )
                SettingsItemCard(
                    icon = Icons.Outlined.History,
                    title = "History",
                    description = "View used & discarded items",
                    onClick = onNavigateToHistory
                )
                SettingsItemCard(
                    icon = Icons.Outlined.Upload,
                    title = if (isImporting) "Importing..." else "Import Data",
                    description = "Restore products from a CSV export",
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
                    title = if (isExporting) "Exporting..." else "Export Data",
                    description = "Export products to CSV",
                    onClick = {
                        if (!isExporting) {
                            isExporting = true
                            scope.launch {
                                try {
                                    val products = productRepository.getAllProducts().first()
                                    if (products.isEmpty()) {
                                        Toast.makeText(context, "No products to export", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val shareIntent = CsvExporter.exportToCSV(context, products)
                                        if (shareIntent != null) {
                                            context.startActivity(Intent.createChooser(shareIntent, "Export Products"))
                                        } else {
                                            Toast.makeText(context, "Failed to create export file", Toast.LENGTH_SHORT).show()
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
                    icon = when {
                        isSyncing -> Icons.Outlined.CloudSync
                        syncStatus.startsWith("Backed up") -> Icons.Outlined.CloudDone
                        else -> Icons.Outlined.CloudUpload
                    },
                    title = if (isSyncing) "Backing up..." else "Backup & Sync",
                    description = syncStatus,
                    // Tapping while signed out would do nothing, so it stays
                    // inert rather than pretending to work.
                    enabled = !isSyncing && FirebaseAuth.getInstance().currentUser != null,
                    onClick = {
                        isSyncing = true
                        scope.launch {
                            val result = productSyncer.sync()
                            syncStatus = describeSync(result, syncPreferences.lastSuccessAt())
                            isSyncing = false
                        }
                    }
                )
            }

            // About Section
            SettingsSection(
                title = "About",
                icon = Icons.Outlined.Info
            ) {
                SettingsItemCard(
                    icon = Icons.Outlined.PrivacyTip,
                    title = "Privacy Policy",
                    description = "How we handle your data",
                    onClick = {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.arkayenlabs.com/privacy/freshtrack"))
                        context.startActivity(intent)
                    }
                )

                SettingsItemCard(
                    icon = Icons.Outlined.Star,
                    title = "Rate Us",
                    description = "Rate and review on Play Store",
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
                    title = "Need Help?",
                    description = "Contact support via email",
                    onClick = {
                        val deviceInfo = "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\nAndroid: ${android.os.Build.VERSION.RELEASE}\nApp Version: 1.1.0"
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("hello@arkayenlabs.com"))
                            putExtra(Intent.EXTRA_SUBJECT, "[FreshTrack] Help & Support")
                            putExtra(Intent.EXTRA_TEXT, "\n\n---\n$deviceInfo")
                        }
                        try {
                            context.startActivity(Intent.createChooser(emailIntent, "Contact Support"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
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
                        title = { Text("Sign Out", fontWeight = FontWeight.Bold) },
                        text = { Text("Are you sure you want to sign out?") },
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
                            ) { Text("Sign Out") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
                        }
                    )
                }

                OutlinedButton(
                    onClick = { showSignOutDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                ) {
                    Icon(Icons.Default.Logout, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out", fontWeight = FontWeight.SemiBold)
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
                            "Delete Account",
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
                    text = "FreshTrack",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Track. Save. Never Waste.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Made for a sustainable future",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Open Source Licenses",
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
            title = { Text("Edit Name", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
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
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Save", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }, shape = RoundedCornerShape(12.dp)) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(24.dp)
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
            title = { Text("Delete account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Naming what goes, rather than a vague warning, so the
                    // decision is made with the facts in front of them.
                    Text("This permanently deletes:")
                    Text(
                        "• Your account and sign-in details\n" +
                            "• Every item in your inventory, on this device and in the cloud\n" +
                            "• Your history and impact figures",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "This cannot be undone. If you want to keep your data, " +
                            "cancel and use Export Data first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = deleteConfirmText,
                        onValueChange = { deleteConfirmText = it },
                        singleLine = true,
                        enabled = !isDeletingAccount,
                        label = { Text("Type DELETE to confirm") }
                    )
                }
            },
            confirmButton = {
                Button(
                    // Typing is friction on purpose: a single mis-tap should not
                    // be able to destroy someone's account.
                    enabled = deleteConfirmText.trim().equals("DELETE", ignoreCase = false) &&
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
                                        "Your account and data have been deleted",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onSignOut()
                                }

                                com.example.freshtrack.data.account.AccountDeleter.Result.NeedsRecentLogin -> {
                                    showDeleteAccountDialog = false
                                    Toast.makeText(
                                        context,
                                        "For your security, sign in again and then delete your account",
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
                    Text(if (isDeletingAccount) "Deleting..." else "Delete forever")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDeletingAccount,
                    onClick = { showDeleteAccountDialog = false }
                ) { Text("Cancel") }
            }
        )
    }

    // A summary rather than a toast: skipped duplicates need explaining, or the
    // user assumes the import silently lost their data.
    importSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { importSummary = null },
            icon = { Icon(Icons.Outlined.Upload, contentDescription = null) },
            title = { Text("Import complete") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Added: ${summary.imported}")
                    if (summary.skippedDuplicates > 0) {
                        Text("Already in your list: ${summary.skippedDuplicates}")
                    }
                    if (summary.failedRows > 0) {
                        Text("Rows that could not be read: ${summary.failedRows}")
                    }
                    if (summary.imported == 0 && summary.skippedDuplicates > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Everything in that file was already here, so nothing changed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { importSummary = null }) { Text("Done") }
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
                fontWeight = FontWeight.Bold,
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
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
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(12.dp)
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
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (checked)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (checked)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
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
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
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
                            .clip(RoundedCornerShape(8.dp))
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
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel", fontWeight = FontWeight.SemiBold)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}
/**
 * Wording for the Backup & Sync card.
 *
 * Deliberately plain about the two cases that are not success. A backup that
 * silently is not happening is worse than no backup, because the user stops
 * worrying about it.
 */
private fun describeSync(
    result: com.example.freshtrack.data.sync.SyncResult,
    lastSuccessAt: Long
): String = when (result) {
    is com.example.freshtrack.data.sync.SyncResult.Success ->
        "Backed up just now"

    com.example.freshtrack.data.sync.SyncResult.SkippedSignedOut ->
        "Sign in to back up your items"

    com.example.freshtrack.data.sync.SyncResult.SkippedNotPremium ->
        "Backup is a Premium feature"

    is com.example.freshtrack.data.sync.SyncResult.Retryable ->
        "No connection. Will retry automatically."

    is com.example.freshtrack.data.sync.SyncResult.Failed ->
        "Backup failed. " + describeLastSync(lastSuccessAt)
}

/** "Backed up 3 hours ago", or an honest statement that it never has been. */
private fun describeLastSync(lastSuccessAt: Long): String {
    if (lastSuccessAt <= 0L) return "Not backed up yet"

    val elapsed = System.currentTimeMillis() - lastSuccessAt
    val minutes = elapsed / 60_000
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Backed up just now"
        minutes < 60 -> "Backed up $minutes ${if (minutes == 1L) "minute" else "minutes"} ago"
        hours < 24 -> "Backed up $hours ${if (hours == 1L) "hour" else "hours"} ago"
        else -> "Backed up $days ${if (days == 1L) "day" else "days"} ago"
    }
}
