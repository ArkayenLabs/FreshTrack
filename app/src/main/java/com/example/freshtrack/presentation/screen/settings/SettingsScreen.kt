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
import com.example.freshtrack.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,

    viewModel: SettingsViewModel = koinViewModel()

) {
    val uiState by viewModel.uiState.collectAsState()
    var showDaysDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val productRepository: ProductRepository = koinInject()

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
            // Notifications Section
            SettingsSection(
                title = "Notifications",
                icon = Icons.Outlined.Notifications
            ) {
                SettingsSwitchCard(
                    icon = Icons.Outlined.NotificationsActive,
                    title = "Daily Reminder",
                    description = "Get daily summary of expiring products",
                    checked = uiState.dailyReminderEnabled,
                    onCheckedChange = { viewModel.toggleDailyReminder(it) }
                )

                SettingsItemCard(
                    icon = Icons.Outlined.Schedule,
                    title = "Advance Notice",
                    description = "${uiState.notificationDaysInAdvance} days before expiry",
                    onClick = { showDaysDialog = true }
                )
            }

            // Data & Storage Section
            SettingsSection(
                title = "Data & Storage",
                icon = Icons.Outlined.Storage
            ) {
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
                    icon = Icons.Outlined.CloudUpload,
                    title = "Backup & Sync",
                    description = "Coming in Phase 2",
                    onClick = { },
                    enabled = false
                )
            }

            // About Section
            SettingsSection(
                title = "About",
                icon = Icons.Outlined.Info
            ) {
                SettingsItemCard(
                    icon = Icons.Outlined.AppSettingsAlt,
                    title = "App Version",
                    description = "1.0.2",
                    onClick = { },
                    enabled = false
                )

                SettingsItemCard(
                    icon = Icons.Outlined.Code,
                    title = "Open Source Licenses",
                    description = "View third-party licenses",
                    onClick = onNavigateToLicenses
                )

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
                    icon = Icons.Outlined.Feedback,
                    title = "Send Feedback",
                    description = "Help us improve FreshTrack",
                    onClick = {
                        val deviceInfo = "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\nAndroid: ${android.os.Build.VERSION.RELEASE}\nApp Version: 1.0.2"
                        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                            data = Uri.parse("mailto:")
                            putExtra(Intent.EXTRA_EMAIL, arrayOf("hello@arkayenlabs.com"))
                            putExtra(Intent.EXTRA_SUBJECT, "[FreshTrack] App Feedback")
                            putExtra(Intent.EXTRA_TEXT, "\n\n---\n$deviceInfo")
                        }
                        try {
                            context.startActivity(Intent.createChooser(emailIntent, "Send Feedback"))
                        } catch (e: Exception) {
                            Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
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
                    text = "Made with ❤️ for a sustainable future",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Days Selector Dialog
    if (showDaysDialog) {
        AdvanceNoticeDaysDialog(
            currentDays = uiState.notificationDaysInAdvance,
            onDismiss = { showDaysDialog = false },
            onConfirm = { days ->
                viewModel.updateNotificationDays(days)
                showDaysDialog = false
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