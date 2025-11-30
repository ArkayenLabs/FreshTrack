package com.example.freshtrack.presentation.screen.addproduct

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.freshtrack.presentation.viewmodel.AddEditProductViewModel
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductScreen(
    productId: String?,
    scannedBarcode: String? = null,
    onNavigateBack: () -> Unit,
    onNavigateToScanner: () -> Unit,
    viewModel: AddEditProductViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // Load product if editing
    LaunchedEffect(productId) {
        productId?.let { viewModel.loadProduct(it) }
    }

//    // Handle scanned barcode
//    LaunchedEffect(scannedBarcode) {
//        android.util.Log.d("AddEditProductScreen", "LaunchedEffect triggered with barcode: $scannedBarcode")
//        scannedBarcode?.let {
//            android.util.Log.d("AddEditProductScreen", "Setting barcode in ViewModel: $it")
//            viewModel.updateBarcode(it)
//            // Log the state after update
//            android.util.Log.d("AddEditProductScreen", "Current barcode in UI state: ${viewModel.uiState.value.barcode}")
//        }
//    }

    // Show error snackbar
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

//    // Debug: Log UI state changes
//    LaunchedEffect(uiState.barcode) {
//        android.util.Log.d("AddEditProductScreen", "UI State barcode changed to: ${uiState.barcode}")
//    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (productId == null) "Add Product" else "Edit Product") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Product Name
            OutlinedTextField(
                value = uiState.name,
                onValueChange = { viewModel.updateName(it) },
                label = { Text("Product Name *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Barcode with Scanner Button
//            Row(
//                horizontalArrangement = Arrangement.spacedBy(8.dp)
//            ) {
//                OutlinedTextField(
//                    value = uiState.barcode ?: "",
//                    onValueChange = { viewModel.updateBarcode(it) },
//                    label = { Text("Barcode (Optional)") },
//                    modifier = Modifier.weight(1f),
//                    singleLine = true,
//                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
//                )
//
//                FilledTonalButton(
//                    onClick = onNavigateToScanner,
//                    modifier = Modifier.height(56.dp)
//                ) {
//                    Icon(Icons.Default.QrCodeScanner, "Scan")
//                }
//            }

            // Category Dropdown
            var expandedCategory by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = expandedCategory,
                onExpandedChange = { expandedCategory = it }
            ) {
                OutlinedTextField(
                    value = uiState.selectedCategory,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expandedCategory) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )

                ExposedDropdownMenu(
                    expanded = expandedCategory,
                    onDismissRequest = { expandedCategory = false }
                ) {
                    uiState.availableCategories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                viewModel.updateCategory(category.name)
                                expandedCategory = false
                            }
                        )
                    }
                }
            }

            // Expiry Date Picker
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = if (uiState.expiryDate > 0) uiState.expiryDate else System.currentTimeMillis()
            )
            var showDatePicker by remember { mutableStateOf(false) }

            OutlinedTextField(
                value = if (uiState.expiryDate > 0) formatDate(uiState.expiryDate) else "",
                onValueChange = {},
                readOnly = true,
                label = { Text("Expiry Date *") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.CalendarToday, "Pick Date")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                datePickerState.selectedDateMillis?.let {
                                    viewModel.updateExpiryDate(it)
                                }
                                showDatePicker = false
                            }
                        ) {
                            Text("OK")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Cancel")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            // Quantity
            OutlinedTextField(
                value = uiState.quantity.toString(),
                onValueChange = {
                    it.toIntOrNull()?.let { qty -> viewModel.updateQuantity(qty) }
                },
                label = { Text("Quantity") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // Notes
            OutlinedTextField(
                value = uiState.notes,
                onValueChange = { viewModel.updateNotes(it) },
                label = { Text("Notes (Optional)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 4
            )

            // Enable Notifications
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Enable Expiry Notifications")
                Switch(
                    checked = uiState.notificationEnabled,
                    onCheckedChange = { viewModel.toggleNotification(it) }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Save Button
            Button(
                onClick = {
                    viewModel.saveProduct(onSuccess = onNavigateBack)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !uiState.isSaving
            ) {
                if (uiState.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text(if (productId == null) "Add Product" else "Save Changes")
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}