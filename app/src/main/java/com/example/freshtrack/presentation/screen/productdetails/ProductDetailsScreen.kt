package com.example.freshtrack.presentation.screen.productdetails

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.freshtrack.presentation.component.CategoryChip
import com.example.freshtrack.presentation.component.ExpiryBadge
import com.example.freshtrack.presentation.component.LoadingState
import com.example.freshtrack.presentation.viewmodel.ProductDetailsViewModel
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

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
    var showActionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(productId) {
        viewModel.loadProduct(productId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Product Details") },
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
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
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
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Product Name
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Expiry Badge
                    ExpiryBadge(
                        daysRemaining = product.daysUntilExpiry(),
                        urgency = product.getUrgency(),
                        modifier = Modifier.align(Alignment.Start)
                    )

                    HorizontalDivider()

                    // Details Card
                    Card(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            DetailRow(label = "Category", value = {
                                CategoryChip(category = product.category)
                            })

                            DetailRow(
                                label = "Expiry Date",
                                value = { Text(formatDate(product.expiryDate)) }
                            )

                            DetailRow(
                                label = "Added On",
                                value = { Text(formatDate(product.addedDate)) }
                            )

                            DetailRow(
                                label = "Quantity",
                                value = { Text(product.quantity.toString()) }
                            )

                            product.barcode?.let {
                                DetailRow(
                                    label = "Barcode",
                                    value = { Text(it) }
                                )
                            }

                            product.notes?.let {
                                DetailRow(
                                    label = "Notes",
                                    value = { Text(it) }
                                )
                            }

                            DetailRow(
                                label = "Notifications",
                                value = {
                                    Text(if (product.notificationEnabled) "Enabled" else "Disabled")
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                viewModel.markAsConsumed(onSuccess = onNavigateBack)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CheckCircle, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Consumed")
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.markAsDiscarded(onSuccess = onNavigateBack)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Delete, null)
                            Spacer(Modifier.width(8.dp))
                            Text("Discarded")
                        }
                    }
                }
            }
        }
    }

    // Delete Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Product") },
            text = { Text("Are you sure you want to delete this product? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProduct(onSuccess = onNavigateBack)
                        showDeleteDialog = false
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        value()
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}