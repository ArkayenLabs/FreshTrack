package com.example.freshtrack.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshtrack.data.local.entities.LocationEntity
import com.example.freshtrack.data.repository.CategoryRepository
import com.example.freshtrack.data.repository.ItemRepository
import com.example.freshtrack.data.repository.LocationRepository
import com.example.freshtrack.domain.model.Category
import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.ExpiryDate
import com.example.freshtrack.domain.model.Item
import com.example.freshtrack.domain.repository.ProductLookupRepository
import com.example.freshtrack.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Adding or editing one item.
 *
 * The date the user picks is user-sourced and confirmed, which is what stops a
 * later scan or shelf-life rule from quietly replacing it. A date that arrived
 * from recognition keeps its own provenance until the user edits or confirms it.
 */
class AddEditItemViewModel(
    private val itemRepository: ItemRepository,
    private val categoryRepository: CategoryRepository,
    private val locationRepository: LocationRepository,
    private val productLookupRepository: ProductLookupRepository,
    private val clock: AppClock = AppClock.System
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditItemUiState())
    val uiState: StateFlow<AddEditItemUiState> = _uiState.asStateFlow()

    init {
        loadCategories()
        loadLocations()
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { categories ->
                _uiState.update { state ->
                    state.copy(
                        availableCategories = categories,
                        selectedCategory = state.selectedCategory.ifBlank {
                            categories.firstOrNull()?.name ?: "Fresh Produce"
                        }
                    )
                }
            }
        }
    }

    private fun loadLocations() {
        viewModelScope.launch {
            locationRepository.observeLocations().collect { locations ->
                _uiState.update { it.copy(availableLocations = locations) }
            }
        }
    }

    fun loadItem(itemId: String) {
        viewModelScope.launch {
            val item = itemRepository.getItem(itemId) ?: return@launch
            _uiState.update {
                it.copy(
                    itemId = item.id,
                    name = item.name,
                    barcode = item.barcode,
                    selectedCategory = item.category,
                    locationId = item.locationId,
                    expiryDate = item.expiry.value,
                    // Carried so an unchanged date keeps the provenance it had
                    // instead of being downgraded to "the user typed this".
                    loadedExpiry = item.expiry,
                    quantity = item.quantity.toString(),
                    notes = item.notes.orEmpty(),
                    imageUri = item.imageUri,
                    isEditMode = true
                )
            }
        }
    }

    fun setBarcodeFromScanner(barcode: String?) {
        val scanned = barcode ?: return
        _uiState.update { it.copy(barcode = scanned) }

        viewModelScope.launch {
            _uiState.update { it.copy(isLookingUp = true, error = null) }
            productLookupRepository.getProductByBarcode(scanned)
                .onSuccess { info ->
                    _uiState.update { state ->
                        state.copy(
                            // A barcode identifies a product, never the expiry
                            // of the packet in your hand, so only the name and
                            // image are taken from the lookup.
                            name = state.name.ifBlank { info.name },
                            imageUri = info.imageUrl ?: state.imageUri,
                            isLookingUp = false
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isLookingUp = false, error = "Product not found or lookup failed")
                    }
                }
        }
    }

    fun updateName(name: String) = _uiState.update { it.copy(name = name) }

    fun updateBarcode(barcode: String) = _uiState.update { it.copy(barcode = barcode) }

    fun updateCategory(category: String) = _uiState.update { it.copy(selectedCategory = category) }

    fun updateLocation(locationId: String?) = _uiState.update { it.copy(locationId = locationId) }

    fun updateExpiryDate(date: LocalDate) = _uiState.update {
        // Picking a date makes it the user's, superseding whatever it was.
        it.copy(expiryDate = date, loadedExpiry = null)
    }

    fun updateDateKind(kind: DateKind) = _uiState.update { it.copy(dateKind = kind) }

    fun updateQuantity(quantity: String) {
        if (quantity.isEmpty() || quantity.all(Char::isDigit)) {
            _uiState.update { it.copy(quantity = quantity.take(3)) }
        }
    }

    fun updateNotes(notes: String) = _uiState.update { it.copy(notes = notes) }

    fun updateImageUri(uri: String) = _uiState.update { it.copy(imageUri = uri) }

    fun saveItem(onSuccess: () -> Unit) {
        val state = _uiState.value

        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Item name is required") }
            return
        }
        val expiry = state.expiryDate
        if (expiry == null) {
            _uiState.update { it.copy(error = "Expiry date is required") }
            return
        }
        val quantity = state.quantity.toIntOrNull()
        if (quantity == null || quantity < 1) {
            _uiState.update { it.copy(error = "Please enter a valid quantity (1-999)") }
            return
        }
        if (quantity > 999) {
            _uiState.update { it.copy(error = "Maximum quantity is 999") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val now = clock.nowMillis()
                // Keep the loaded provenance when the date was not touched;
                // otherwise this is the user's own date.
                val expiryDate = state.loadedExpiry?.takeIf { it.value == expiry }
                    ?: ExpiryDate.enteredByUser(expiry, state.dateKind, now)

                val item = Item(
                    id = state.itemId,
                    name = state.name.trim(),
                    category = state.selectedCategory,
                    expiry = expiryDate,
                    quantity = quantity,
                    locationId = state.locationId,
                    barcode = state.barcode?.takeIf(String::isNotBlank),
                    notes = state.notes.takeIf(String::isNotBlank),
                    imageUri = state.imageUri,
                    addedAt = now
                )

                if (state.isEditMode) itemRepository.update(item) else itemRepository.add(item)

                _uiState.update { it.copy(isSaving = false) }
                onSuccess()
            } catch (e: Exception) {
                // Deliberately not e.message: that surfaces internal exception
                // text to the user and can leak implementation detail.
                _uiState.update {
                    it.copy(isSaving = false, error = "Could not save this item. Please try again.")
                }
            }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
}

data class AddEditItemUiState(
    val itemId: String = "",
    val name: String = "",
    val barcode: String? = null,
    val selectedCategory: String = "",
    val availableCategories: List<Category> = emptyList(),
    val locationId: String? = null,
    val availableLocations: List<LocationEntity> = emptyList(),
    val expiryDate: LocalDate? = null,
    val dateKind: DateKind = DateKind.BEST_BEFORE,
    /** Provenance of a date loaded for editing, kept while it is unchanged. */
    val loadedExpiry: ExpiryDate? = null,
    val quantity: String = "",
    val notes: String = "",
    val imageUri: String? = null,
    val isEditMode: Boolean = false,
    val isSaving: Boolean = false,
    val isLookingUp: Boolean = false,
    val error: String? = null
)
