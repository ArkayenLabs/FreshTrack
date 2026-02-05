package com.example.freshtrack.presentation.screen.productdetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.freshtrack.presentation.component.LoadingState
import com.example.freshtrack.presentation.viewmodel.ProductDetailsViewModel
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailsScreen(
    productId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: ProductDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showConsumeDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var selectedQuantity by remember { mutableIntStateOf(1) }

    LaunchedEffect(productId) {
        viewModel.loadProduct(productId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Product Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToEdit(productId) }) {
                        Icon(Icons.Default.Edit, "Edit")
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                LoadingState("Loading product details...")
            }
        } else {
            uiState.product?.let { product ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Spacer(Modifier.height(4.dp))

                    // Product Name & Expiry Status
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Expiry Status Badge
                        ExpiryStatusBadge(expiryDate = product.expiryDate)
                    }

                    // Product Information Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Category
                            InfoRow(
                                icon = Icons.Outlined.Category,
                                label = "Category",
                                value = product.category,
                                showCategoryChip = true
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Expiry Date
                            InfoRow(
                                icon = Icons.Outlined.CalendarToday,
                                label = "Expiry Date",
                                value = formatDate(product.expiryDate)
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Added On
                            InfoRow(
                                icon = Icons.Outlined.Schedule,
                                label = "Added On",
                                value = formatDate(product.addedDate)
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Quantity
                            InfoRow(
                                icon = Icons.Outlined.Inventory,
                                label = "Quantity",
                                value = product.quantity.toString()
                            )

                            // Barcode (if available)
                            product.barcode?.let { barcode ->
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                InfoRow(
                                    icon = Icons.Outlined.QrCode,
                                    label = "Barcode",
                                    value = barcode
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Notifications
                            InfoRow(
                                icon = if (product.notificationEnabled)
                                    Icons.Outlined.Notifications
                                else
                                    Icons.Outlined.NotificationsOff,
                                label = "Notifications",
                                value = if (product.notificationEnabled) "Enabled" else "Disabled",
                                valueColor = if (product.notificationEnabled)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Notes Card (if available)
                    product.notes?.let { notes ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "Notes",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (product.quantity > 1) {
                                    selectedQuantity = 1
                                    showConsumeDialog = true
                                } else {
                                    viewModel.markAsConsumed(onSuccess = onNavigateBack)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Consumed",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (product.quantity > 1) {
                                    selectedQuantity = 1
                                    showDiscardDialog = true
                                } else {
                                    viewModel.markAsDiscarded(onSuccess = onNavigateBack)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                width = 1.5.dp
                            )
                        ) {
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Discarded",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            },
            title = {
                Text(
                    "Delete Product?",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "Are you sure you want to delete this product? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProduct(onSuccess = onNavigateBack)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
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

    // Consume Quantity Dialog
    if (showConsumeDialog) {
        val maxQty = uiState.product?.quantity ?: 1
        QuantityPickerDialog(
            title = "Consume How Many?",
            maxQuantity = maxQty,
            selectedQuantity = selectedQuantity,
            onQuantityChange = { selectedQuantity = it },
            onConfirm = {
                viewModel.consumeQuantity(selectedQuantity, onSuccess = onNavigateBack)
                showConsumeDialog = false
            },
            onDismiss = { showConsumeDialog = false },
            confirmText = "Consume",
            icon = Icons.Default.CheckCircle,
            iconTint = MaterialTheme.colorScheme.primary
        )
    }

    // Discard Quantity Dialog
    if (showDiscardDialog) {
        val maxQty = uiState.product?.quantity ?: 1
        QuantityPickerDialog(
            title = "Discard How Many?",
            maxQuantity = maxQty,
            selectedQuantity = selectedQuantity,
            onQuantityChange = { selectedQuantity = it },
            onConfirm = {
                viewModel.discardQuantity(selectedQuantity, onSuccess = onNavigateBack)
                showDiscardDialog = false
            },
            onDismiss = { showDiscardDialog = false },
            confirmText = "Discard",
            icon = Icons.Outlined.Delete,
            iconTint = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun ExpiryStatusBadge(expiryDate: Long) {
    // Calculate days using calendar dates (not raw time difference)
    val calendar = java.util.Calendar.getInstance()
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    val todayMidnight = calendar.timeInMillis
    
    calendar.timeInMillis = expiryDate
    calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
    calendar.set(java.util.Calendar.MINUTE, 0)
    calendar.set(java.util.Calendar.SECOND, 0)
    calendar.set(java.util.Calendar.MILLISECOND, 0)
    val expiryMidnight = calendar.timeInMillis
    
    val daysUntilExpiry = TimeUnit.MILLISECONDS.toDays(expiryMidnight - todayMidnight)

    val (status, color, icon) = when {
        daysUntilExpiry < 0 -> Triple("Expired", MaterialTheme.colorScheme.error, Icons.Outlined.ErrorOutline)
        daysUntilExpiry == 0L -> Triple("Expires Today", MaterialTheme.colorScheme.error, Icons.Outlined.Warning)
        daysUntilExpiry <= 3 -> Triple("Expiring Soon", MaterialTheme.colorScheme.error, Icons.Outlined.Warning)
        daysUntilExpiry <= 7 -> Triple("Expiring This Week", MaterialTheme.colorScheme.tertiary, Icons.Outlined.Schedule)
        else -> Triple("Fresh", MaterialTheme.colorScheme.primary, Icons.Outlined.CheckCircle)
    }

    Surface(
        shape = RoundedCornerShape(50.dp),
        color = color.copy(alpha = 0.15f),
        modifier = Modifier.wrapContentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = color
                )
            }

            Column {
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Text(
                    text = if (daysUntilExpiry >= 0) {
                        when (daysUntilExpiry) {
                            0L -> "Today"
                            1L -> "Tomorrow"
                            else -> "$daysUntilExpiry days remaining"
                        }
                    } else {
                        "${-daysUntilExpiry} days ago"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = color.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    showCategoryChip: Boolean = false,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (showCategoryChip) {
            CategoryChipCompact(category = value)
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = valueColor
            )
        }
    }
}

@Composable
fun CategoryChipCompact(category: String) {
    val (icon, color) = when (category) {
        "Food" -> Icons.Outlined.Restaurant to MaterialTheme.colorScheme.primary
        "Cosmetics" -> Icons.Outlined.Face to MaterialTheme.colorScheme.secondary
        "Medicines" -> Icons.Outlined.MedicalServices to MaterialTheme.colorScheme.error
        "Beverages" -> Icons.Outlined.LocalCafe to MaterialTheme.colorScheme.tertiary
        else -> Icons.Outlined.Category to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = color
            )
            Text(
                text = category,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@Composable
fun QuantityPickerDialog(
    title: String,
    maxQuantity: Int,
    selectedQuantity: Int,
    onQuantityChange: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String,
    icon: ImageVector,
    iconTint: androidx.compose.ui.graphics.Color
) {
    var textValue by remember { mutableStateOf(selectedQuantity.toString()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun validateAndUpdate(input: String) {
        textValue = input
        val qty = input.toIntOrNull()
        when {
            input.isEmpty() -> {
                errorMessage = null
            }
            qty == null -> {
                errorMessage = "Enter a valid number"
            }
            qty < 1 -> {
                errorMessage = "Minimum is 1"
            }
            qty > maxQuantity -> {
                errorMessage = "Maximum is $maxQuantity"
            }
            else -> {
                errorMessage = null
                onQuantityChange(qty)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { validateAndUpdate(it) },
                    label = { Text("Quantity") },
                    placeholder = { Text("1-$maxQuantity") },
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text("Available: $maxQuantity")
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Quick action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (maxQuantity > 1) {
                        FilterChip(
                            selected = textValue == "1",
                            onClick = { validateAndUpdate("1") },
                            label = { Text("1") }
                        )
                    }
                    if (maxQuantity >= 5) {
                        FilterChip(
                            selected = textValue == "5",
                            onClick = { validateAndUpdate("5") },
                            label = { Text("5") }
                        )
                    }
                    if (maxQuantity >= 10) {
                        FilterChip(
                            selected = textValue == "10",
                            onClick = { validateAndUpdate("10") },
                            label = { Text("10") }
                        )
                    }
                    FilterChip(
                        selected = textValue == maxQuantity.toString(),
                        onClick = { validateAndUpdate(maxQuantity.toString()) },
                        label = { Text("All") }
                    )
                }

                if (textValue == maxQuantity.toString() && errorMessage == null) {
                    Text(
                        "This will remove the product completely",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = errorMessage == null && textValue.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = iconTint
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(confirmText, fontWeight = FontWeight.SemiBold)
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