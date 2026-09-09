package com.example.freshtrack.presentation.screen.addproduct

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.freshtrack.domain.model.DateSource
import com.example.freshtrack.domain.model.ExpiryDate
import com.example.freshtrack.presentation.viewmodel.AddEditItemViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditProductScreen(
    productId: String?,
    onNavigateBack: () -> Unit,
    scannedBarcode: String? = null,
    scannedExpiry: ExpiryDate? = null,
    onNavigateToScanner: () -> Unit,
    onNavigateToDateScanner: () -> Unit = {},
    viewModel: AddEditItemViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Load product if editing
    LaunchedEffect(productId) {
        productId?.let { viewModel.loadItem(it) }
    }

    // Update barcode from scanner
    LaunchedEffect(scannedExpiry) {
        scannedExpiry?.let { viewModel.applyScannedExpiry(it) }
    }

    LaunchedEffect(scannedBarcode) {
        scannedBarcode?.let { viewModel.updateBarcode(it) }
    }

    // Show error snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (productId == null) "Add Product" else "Edit Product",
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            // Sticky Bottom Action Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Button(
                    onClick = {
                        viewModel.saveItem(onSuccess = onNavigateBack)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .height(56.dp),
                    enabled = !uiState.isSaving,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = if (productId == null) Icons.Default.Add else Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (productId == null) "Add Product" else "Save Changes",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Spacer(Modifier.height(4.dp))

            // SECTION 1: Basic Information
            SectionCard(title = "Basic Information", icon = Icons.Outlined.Info) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Product Name
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = { viewModel.updateName(it) },
                        label = { Text("Product Name") },
                        placeholder = { Text("e.g., Fresh Milk") },
                        leadingIcon = {
                            Icon(Icons.Outlined.ShoppingBag, "Product")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )

                    // Barcode with Scanner
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.barcode ?: "",
                            onValueChange = { viewModel.updateBarcode(it) },
                            label = { Text("Barcode") },
                            placeholder = { Text("Optional") },
                            leadingIcon = {
                                Icon(Icons.Outlined.QrCode, "Barcode")
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
                        )

                        FilledTonalButton(
                            onClick = onNavigateToScanner,
                            modifier = Modifier.height(56.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.QrCodeScanner, "Scan", modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            // SECTION 2: Category Selection
            SectionCard(title = "Category", icon = Icons.Outlined.Category) {
                CategoryChipGroup(
                    categories = uiState.availableCategories,
                    selectedCategory = uiState.selectedCategory,
                    onCategorySelected = { viewModel.updateCategory(it) }
                )
            }

            // SECTION 3: Expiry & Quantity
            SectionCard(title = "Expiry & Quantity", icon = Icons.Outlined.Schedule) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Expiry Date Picker
                    ExpiryDatePicker(
                        expiryDate = uiState.expiryDate,
                        onDateSelected = { viewModel.updateExpiryDate(it) }
                    )

                    // Reading the date off the packet is the fast path, but it
                    // sits beside the picker rather than replacing it: the
                    // manual route stays one tap away, which is what makes it
                    // safe for the camera to refuse an unclear label.
                    OutlinedButton(
                        onClick = onNavigateToDateScanner,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Outlined.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Scan the printed date")
                    }

                    if (uiState.carriedExpiry?.source == DateSource.PRINTED_OCR) {
                        // Says where the date came from, so an item whose date
                        // was read rather than typed is visibly so.
                        Text(
                            text = "Read from the packet and confirmed by you.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Quantity
                    OutlinedTextField(
                        value = uiState.quantity,
                        onValueChange = { viewModel.updateQuantity(it) },
                        label = { Text("Quantity") },
                        placeholder = { Text("Enter quantity") },
                        leadingIcon = {
                            Icon(Icons.Outlined.Inventory, "Quantity")
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        supportingText = {
                            Text(
                                "Range: 1-999",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                        )
                    )
                }
            }

            // SECTION 4: Additional Details
            SectionCard(title = "Additional Details(Optional)", icon = Icons.Outlined.Description) {
                OutlinedTextField(
                    value = uiState.notes,
                    onValueChange = { viewModel.updateNotes(it) },
                    label = { Text("Notes") },
                    placeholder = { Text("Add any additional information...") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    maxLines = 4,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(Modifier.height(80.dp)) // Space for bottom button
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
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
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            content()
        }
    }
}

@Composable
fun CategoryChipGroup(
    categories: List<com.example.freshtrack.domain.model.Category>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        categories.chunked(3).forEach { rowCategories ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowCategories.forEach { category ->
                    CategoryChip(
                        category = category,
                        isSelected = category.name == selectedCategory,
                        onSelected = { onCategorySelected(category.name) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Fill remaining space if less than 3 items
                repeat(3 - rowCategories.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun CategoryChip(
    category: com.example.freshtrack.domain.model.Category,
    isSelected: Boolean,
    onSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Maps the actual food categories. Previously mapped removed categories
    // (Food/Cosmetics/Medicines), so every real category fell to the default and
    // they all showed the same icon.
    val icon = when (category.name) {
        "Fresh Produce" -> Icons.Outlined.Eco
        "Dairy" -> Icons.Outlined.WaterDrop
        "Bakery" -> Icons.Outlined.BakeryDining
        "Beverages" -> Icons.Outlined.LocalCafe
        "Pantry" -> Icons.Outlined.Kitchen
        "Leftovers" -> Icons.Outlined.TakeoutDining
        else -> Icons.Outlined.Category
    }

    FilterChip(
        selected = isSelected,
        onClick = onSelected,
        label = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    category.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    // "Fresh Produce" is long enough to wrap. Center it over up to
                    // two lines so it reads cleanly instead of wrapping raggedly;
                    // the chip is tall enough (72dp) to hold two centered lines.
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        },
        modifier = modifier.height(72.dp),
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = if (isSelected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
            selectedBorderColor = MaterialTheme.colorScheme.primary,
            borderWidth = if (isSelected) 2.dp else 1.dp
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpiryDatePicker(
    expiryDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit
) {
    val today = remember { LocalDate.now() }

    // Material's date picker works in UTC millis. Converting through UTC in
    // both directions keeps the round trip exact; mixing the local zone into
    // one side is what makes a picked date come back a day earlier.
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = (expiryDate ?: today).toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                !utcTimeMillis.toLocalDateUtc().isBefore(today)

            override fun isSelectableYear(year: Int): Boolean = year >= today.year
        }
    )
    var showDatePicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = expiryDate?.let { formatDate(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Expiry Date") },
            placeholder = { Text("Select expiry date") },
            leadingIcon = {
                Icon(Icons.Outlined.CalendarToday, "Calendar")
            },
            trailingIcon = {
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.Edit, "Pick Date")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            ),
            interactionSource = remember {
                object : MutableInteractionSource {
                    override val interactions = MutableSharedFlow<Interaction>()
                    override suspend fun emit(interaction: Interaction) {
                        if (interaction is PressInteraction.Press) {
                            showDatePicker = true
                        }
                    }
                    override fun tryEmit(interaction: Interaction): Boolean {
                        return if (interaction is PressInteraction.Press) {
                            showDatePicker = true
                            true
                        } else false
                    }
                }
            }
        )

        if (expiryDate != null) {
            ExpiryStatusIndicator(expiryDate, today)
        }
    }

    if (showDatePicker) {
        // Haptics suppressed inside the picker: it fires on every date scrolled
        // past, which turns choosing a date months out into continuous buzzing.
        CompositionLocalProvider(
            LocalHapticFeedback provides object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
            }
        ) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            datePickerState.selectedDateMillis?.let {
                                onDateSelected(it.toLocalDateUtc())
                            }
                            showDatePicker = false
                        }
                    ) {
                        Text("Confirm")
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
    }
}

@Composable
fun ExpiryStatusIndicator(expiryDate: LocalDate, today: LocalDate) {
    val daysUntilExpiry = ChronoUnit.DAYS.between(today, expiryDate)

    val (status, color, icon) = when {
        daysUntilExpiry < 0 ->
            Triple("Expired", MaterialTheme.colorScheme.error, Icons.Outlined.ErrorOutline)
        daysUntilExpiry <= 3 ->
            Triple("Expiring Soon", MaterialTheme.colorScheme.error, Icons.Outlined.Warning)
        daysUntilExpiry <= 7 ->
            Triple("Expiring This Week", MaterialTheme.colorScheme.tertiary, Icons.Outlined.Schedule)
        else ->
            Triple("Fresh", MaterialTheme.colorScheme.primary, Icons.Outlined.CheckCircle)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = color
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = color
                )
            }
            Text(
                text = if (daysUntilExpiry >= 0) "$daysUntilExpiry days" else "Expired",
                style = MaterialTheme.typography.bodySmall,
                color = color
            )
        }
    }
}

private val displayDateFormat: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.getDefault())

private fun formatDate(date: LocalDate): String = date.format(displayDateFormat)

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDateUtc(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
