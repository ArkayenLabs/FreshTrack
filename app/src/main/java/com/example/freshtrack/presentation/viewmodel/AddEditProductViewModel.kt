package com.example.freshtrack.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshtrack.data.repository.CategoryRepository
import com.example.freshtrack.data.repository.ProductRepository
import com.example.freshtrack.domain.model.Category
import com.example.freshtrack.domain.model.Product
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * ViewModel for Add/Edit Product Screen
 * Handles product creation and editing logic
 */
class AddEditProductViewModel(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow(AddEditProductUiState())
    val uiState: StateFlow<AddEditProductUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { categories ->
                _uiState.update { it.copy(
                    availableCategories = categories,
                    selectedCategory = categories.firstOrNull()?.name ?: "Food"
                )}
            }
        }
    }

    /**
     * Load existing product for editing
     */
    fun loadProduct(productId: String) {
        viewModelScope.launch {
            productRepository.getProductById(productId).collect { product ->
                product?.let {
                    _uiState.update { state -> state.copy(
                        productId = it.id,
                        name = it.name,
                        barcode = it.barcode,
                        selectedCategory = it.category,
                        expiryDate = it.expiryDate,
                        quantity = it.quantity.toString(),  // Convert Int to String
                        notes = it.notes ?: "",
                        imageUri = it.imageUri,
                        notificationEnabled = it.notificationEnabled,
                        isEditMode = true
                    )}
                }
            }
        }
    }

    /**
     * Set barcode from scanner
     */
    fun setBarcodeFromScanner(barcode: String?) {
        barcode?.let {
            _uiState.update { state -> state.copy(barcode = it) }
        }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name) }
    }

    fun updateBarcode(barcode: String) {
        _uiState.update { it.copy(barcode = barcode) }
    }

    fun updateCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
    }

    fun updateExpiryDate(timestamp: Long) {
        _uiState.update { it.copy(expiryDate = timestamp) }
    }

    fun updateQuantity(quantity: String) {
        // Only allow numbers
        if (quantity.isEmpty() || quantity.all { it.isDigit() }) {
            _uiState.update { it.copy(quantity = quantity) }
        }
    }

    fun updateNotes(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun updateImageUri(uri: String) {
        _uiState.update { it.copy(imageUri = uri) }
    }

    fun toggleNotification(enabled: Boolean) {
        _uiState.update { it.copy(notificationEnabled = enabled) }
    }

    /**
     * Save or update product
     */
    fun saveProduct(onSuccess: () -> Unit) {
        val state = _uiState.value

        // Validation
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Product name is required") }
            return
        }

        if (state.expiryDate == 0L) {
            _uiState.update { it.copy(error = "Expiry date is required") }
            return
        }

        // Validate quantity
        val quantityInt = state.quantity.toIntOrNull()
        if (quantityInt == null || quantityInt < 1) {
            _uiState.update { it.copy(error = "Please enter a valid quantity (minimum 1)") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            try {
                val product = Product(
                    id = if (state.isEditMode) state.productId else UUID.randomUUID().toString(),
                    name = state.name.trim(),
                    barcode = state.barcode?.takeIf { it.isNotBlank() },
                    category = state.selectedCategory,
                    expiryDate = state.expiryDate,
                    addedDate = System.currentTimeMillis(),
                    quantity = quantityInt,  // Use validated quantity
                    notes = state.notes.takeIf { it.isNotBlank() },
                    imageUri = state.imageUri,
                    notificationEnabled = state.notificationEnabled,
                    isConsumed = false,
                    isDiscarded = false
                )

                if (state.isEditMode) {
                    productRepository.updateProduct(product)
                } else {
                    productRepository.insertProduct(product)
                }

                _uiState.update { it.copy(isSaving = false) }
                onSuccess()

            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isSaving = false,
                    error = "Failed to save product: ${e.message}"
                )}
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

/**
 * UI State for Add/Edit Product
 */
data class AddEditProductUiState(
    val productId: String = "",
    val name: String = "",
    val barcode: String? = null,
    val selectedCategory: String = "Food",
    val availableCategories: List<Category> = emptyList(),
    val expiryDate: Long = 0L,
    val quantity: String = "",  // Changed from Int to String, default empty
    val notes: String = "",
    val imageUri: String? = null,
    val notificationEnabled: Boolean = true,
    val isEditMode: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for Product Details Screen
 * Displays detailed information about a single product
 */
class ProductDetailsViewModel(
    private val productRepository: ProductRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProductDetailsUiState())
    val uiState: StateFlow<ProductDetailsUiState> = _uiState.asStateFlow()

    fun loadProduct(productId: String) {
        viewModelScope.launch {
            productRepository.getProductById(productId).collect { product ->
                _uiState.update { it.copy(
                    product = product,
                    isLoading = false
                )}
            }
        }
    }

    fun deleteProduct(onSuccess: () -> Unit) {
        val productId = _uiState.value.product?.id ?: return
        viewModelScope.launch {
            productRepository.deleteProduct(productId)
            onSuccess()
        }
    }

    fun markAsConsumed(onSuccess: () -> Unit) {
        val productId = _uiState.value.product?.id ?: return
        viewModelScope.launch {
            productRepository.markAsConsumed(productId)
            onSuccess()
        }
    }

    fun markAsDiscarded(onSuccess: () -> Unit) {
        val productId = _uiState.value.product?.id ?: return
        viewModelScope.launch {
            productRepository.markAsDiscarded(productId)
            onSuccess()
        }
    }
}

/**
 * UI State for Product Details
 */
data class ProductDetailsUiState(
    val product: Product? = null,
    val isLoading: Boolean = true
)

/**
 * ViewModel for Settings Screen
 * Manages app preferences and configuration
 */
class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    // Settings will be persisted using DataStore in future implementation

    fun updateNotificationDays(days: Int) {
        _uiState.update { it.copy(notificationDaysInAdvance = days) }
    }

    fun toggleDailyReminder(enabled: Boolean) {
        _uiState.update { it.copy(dailyReminderEnabled = enabled) }
    }
}

/**
 * UI State for Settings
 */
data class SettingsUiState(
    val notificationDaysInAdvance: Int = 3,
    val dailyReminderEnabled: Boolean = true,
    val darkModeEnabled: Boolean = false
)