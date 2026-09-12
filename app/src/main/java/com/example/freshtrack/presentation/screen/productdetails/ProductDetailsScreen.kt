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
import androidx.compose.ui.res.stringResource
import com.example.freshtrack.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.freshtrack.presentation.component.LoadingState
import com.example.freshtrack.presentation.viewmodel.ItemDetailsViewModel
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailsScreen(
    productId: String,
    onNavigateBack: () -> Unit,
    onNavigateToEdit: (String) -> Unit,
    viewModel: ItemDetailsViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showConsumeDialog by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var selectedQuantity by remember { mutableIntStateOf(1) }

    LaunchedEffect(productId) {
        viewModel.loadItem(productId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.details_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToEdit(productId) }) {
                        Icon(Icons.Default.Edit, stringResource(R.string.action_edit))
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, stringResource(R.string.action_delete))
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
                LoadingState(stringResource(R.string.details_loading))
            }
        } else {
            uiState.item?.let { product ->
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
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        // Expiry Status Badge
                        ExpiryStatusBadge(expiryDate = product.expiry.value)
                    }

                    // Product Information Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                                label = stringResource(R.string.add_section_category),
                                value = product.category,
                                showCategoryChip = true
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Expiry Date
                            InfoRow(
                                icon = Icons.Outlined.CalendarToday,
                                label = stringResource(R.string.add_expiry_label),
                                value = formatDate(product.expiry.value)
                            )
                            // A guess says so, here and on the card and on
                            // Today. This is the one screen with room to say
                            // what the guess was based on and how to replace it.
                            if (product.needsDateReview || product.hasEstimatedDate) {
                                Text(
                                    text = stringResource(R.string.details_date_estimated_note),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 32.dp, top = 4.dp)
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Added On
                            InfoRow(
                                icon = Icons.Outlined.Schedule,
                                label = stringResource(R.string.details_added_on),
                                value = formatDateTime(product.addedAt)
                            )

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Quantity
                            InfoRow(
                                icon = Icons.Outlined.Inventory,
                                label = stringResource(R.string.add_quantity_label),
                                value = product.quantity.toString()
                            )

                            // Barcode (if available)
                            product.barcode?.let { barcode ->
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                InfoRow(
                                    icon = Icons.Outlined.QrCode,
                                    label = stringResource(R.string.add_barcode_label),
                                    value = barcode
                                )
                            }

                        }
                    }

                    // Notes Card (if available)
                    product.notes?.let { notes ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
                                        text = stringResource(R.string.add_notes_label),
                                        style = MaterialTheme.typography.titleMedium,
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
                                    viewModel.use(product.quantity, onResolved = onNavigateBack)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.details_used),
                                style = MaterialTheme.typography.titleSmall
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                if (product.quantity > 1) {
                                    selectedQuantity = 1
                                    showDiscardDialog = true
                                } else {
                                    viewModel.discard(product.quantity, onResolved = onNavigateBack)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = MaterialTheme.shapes.medium,
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
                                stringResource(R.string.details_discarded),
                                style = MaterialTheme.typography.titleSmall
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
                    stringResource(R.string.kitchen_delete_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    stringResource(R.string.kitchen_delete_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteItem(onSuccess = onNavigateBack)
                        showDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false },
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

    // Consume Quantity Dialog
    if (showConsumeDialog) {
        val maxQty = uiState.item?.quantity ?: 1
        QuantityPickerDialog(
            title = stringResource(R.string.details_use_how_many),
            maxQuantity = maxQty,
            selectedQuantity = selectedQuantity,
            onQuantityChange = { selectedQuantity = it },
            onConfirm = {
                viewModel.use(selectedQuantity, onResolved = onNavigateBack)
                showConsumeDialog = false
            },
            onDismiss = { showConsumeDialog = false },
            confirmText = stringResource(R.string.details_use),
            icon = Icons.Default.CheckCircle,
            iconTint = MaterialTheme.colorScheme.primary
        )
    }

    // Discard Quantity Dialog
    if (showDiscardDialog) {
        val maxQty = uiState.item?.quantity ?: 1
        QuantityPickerDialog(
            title = stringResource(R.string.details_discard_how_many),
            maxQuantity = maxQty,
            selectedQuantity = selectedQuantity,
            onQuantityChange = { selectedQuantity = it },
            onConfirm = {
                viewModel.discard(selectedQuantity, onResolved = onNavigateBack)
                showDiscardDialog = false
            },
            onDismiss = { showDiscardDialog = false },
            confirmText = stringResource(R.string.details_discard),
            icon = Icons.Outlined.Delete,
            iconTint = MaterialTheme.colorScheme.error
        )
    }
}

@Composable
fun ExpiryStatusBadge(expiryDate: LocalDate) {
    val daysUntilExpiry = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate)

    val (status, color, icon) = when {
        daysUntilExpiry < 0 -> Triple(stringResource(R.string.status_expired), MaterialTheme.colorScheme.error, Icons.Outlined.ErrorOutline)
        daysUntilExpiry == 0L -> Triple(stringResource(R.string.status_expires_today), MaterialTheme.colorScheme.error, Icons.Outlined.Warning)
        daysUntilExpiry <= 3 -> Triple(stringResource(R.string.status_expiring_soon), MaterialTheme.colorScheme.error, Icons.Outlined.Warning)
        daysUntilExpiry <= 7 -> Triple(stringResource(R.string.status_expiring_this_week), MaterialTheme.colorScheme.tertiary, Icons.Outlined.Schedule)
        else -> Triple(stringResource(R.string.status_fresh), MaterialTheme.colorScheme.primary, Icons.Outlined.CheckCircle)
    }

    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.14f),
        modifier = Modifier.wrapContentSize()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = color
            )

            Column {
                Text(
                    text = status,
                    style = MaterialTheme.typography.titleMedium,
                    color = color
                )
                Text(
                    text = if (daysUntilExpiry >= 0) {
                        when (daysUntilExpiry) {
                            0L -> stringResource(R.string.details_today)
                            1L -> stringResource(R.string.details_tomorrow)
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
                style = MaterialTheme.typography.titleSmall,
                color = valueColor
            )
        }
    }
}

@Composable
fun CategoryChipCompact(category: String) {
    // The real category set. This previously mapped Food/Cosmetics/Medicines,
    // which the product does not have, so every chip but Beverages fell through.
    val icon = when (category) {
        "Fresh Produce" -> Icons.Outlined.Eco
        "Dairy & Eggs" -> Icons.Outlined.WaterDrop
        "Meat & Fish" -> Icons.Outlined.SetMeal
        "Ready Meals" -> Icons.Outlined.LunchDining
        "Bakery" -> Icons.Outlined.BakeryDining
        "Beverages" -> Icons.Outlined.LocalCafe
        "Store Cupboard" -> Icons.Outlined.Kitchen
        "Leftovers" -> Icons.Outlined.TakeoutDining
        else -> Icons.Outlined.Category
    }
    val color = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = MaterialTheme.shapes.small,
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
                color = color
            )
        }
    }
}

private val displayDateFormat: DateTimeFormatter
    get() = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun formatDate(date: LocalDate): String = date.format(displayDateFormat)

/** For instants such as "added on", which are points in time, not calendar dates. */
private fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
        .format(displayDateFormat)

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

    // Resolved in composition: the validator below is an ordinary local
    // function and cannot read resources itself.
    val invalidNumber = stringResource(R.string.details_invalid_number)
    val minimumOne = stringResource(R.string.details_minimum_one)
    val maximumIs = stringResource(R.string.details_maximum_is, maxQuantity)

    fun validateAndUpdate(input: String) {
        textValue = input
        val qty = input.toIntOrNull()
        when {
            input.isEmpty() -> {
                errorMessage = null
            }
            qty == null -> {
                errorMessage = invalidNumber
            }
            qty < 1 -> {
                errorMessage = minimumOne
            }
            qty > maxQuantity -> {
                errorMessage = maximumIs
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
                style = MaterialTheme.typography.titleLarge
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
                    label = { Text(stringResource(R.string.add_quantity_label)) },
                    placeholder = { Text("1-$maxQuantity") },
                    isError = errorMessage != null,
                    supportingText = {
                        if (errorMessage != null) {
                            Text(errorMessage!!, color = MaterialTheme.colorScheme.error)
                        } else {
                            Text(stringResource(R.string.details_available, maxQuantity))
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
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
                        label = { Text(stringResource(R.string.details_all)) }
                    )
                }

                if (textValue == maxQuantity.toString() && errorMessage == null) {
                    Text(
                        stringResource(R.string.details_remove_completely),
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
                shape = MaterialTheme.shapes.medium
            ) {
                Text(confirmText)
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