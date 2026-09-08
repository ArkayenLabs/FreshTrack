package com.example.freshtrack.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshtrack.data.repository.CategoryRepository
import com.example.freshtrack.data.repository.ItemRepository
import com.example.freshtrack.domain.model.Category
import com.example.freshtrack.domain.model.ExpiryUrgency
import com.example.freshtrack.domain.model.Item
import com.example.freshtrack.domain.model.ProductFilter
import com.example.freshtrack.domain.model.ProductSort
import com.example.freshtrack.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Overview of what needs attention.
 *
 * Bucketing is done here against a single "today" taken from the clock rather
 * than each item asking the system time for itself, so every row on the screen
 * is classified against the same date. Previously each call recomputed the
 * current time, which meant a list rendered across midnight could place two
 * items with the same date in different buckets.
 */
class DashboardViewModel(
    private val itemRepository: ItemRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: AppClock = AppClock.System
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            combine(
                itemRepository.observeActiveItems(),
                itemRepository.observeExpiredItems(),
                itemRepository.observeActiveCount()
            ) { allItems, expiredItems, activeCount ->
                val today = clock.today()

                DashboardUiState(
                    totalActiveItems = activeCount,
                    expiringToday = allItems.filter { it.daysUntilExpiry(today) == 0L },
                    expiringThisWeek = allItems.filter { it.daysUntilExpiry(today) in 1..7 },
                    safeItems = allItems.filter { it.daysUntilExpiry(today) > 7 },
                    expiredItems = expiredItems,
                    criticalItems = allItems.filter {
                        it.urgency(today) == ExpiryUrgency.CRITICAL
                    },
                    isLoading = false
                )
            }.collect { state -> _uiState.value = state }
        }
    }

    fun markAsUsed(itemId: String) {
        viewModelScope.launch { itemRepository.use(itemId) }
    }

    fun markAsDiscarded(itemId: String) {
        viewModelScope.launch { itemRepository.discard(itemId) }
    }
}

data class DashboardUiState(
    val totalActiveItems: Int = 0,
    val expiringToday: List<Item> = emptyList(),
    val expiringThisWeek: List<Item> = emptyList(),
    val safeItems: List<Item> = emptyList(),
    val expiredItems: List<Item> = emptyList(),
    val criticalItems: List<Item> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * The full inventory, with filtering and sorting.
 */
class ItemListViewModel(
    private val itemRepository: ItemRepository,
    private val categoryRepository: CategoryRepository,
    private val clock: AppClock = AppClock.System
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemListUiState())
    val uiState: StateFlow<ItemListUiState> = _uiState.asStateFlow()

    private val _currentFilter = MutableStateFlow(ProductFilter.ALL)
    private val _currentSort = MutableStateFlow(ProductSort.EXPIRY_DATE_ASC)
    private val _selectedCategory = MutableStateFlow<String?>(null)

    init {
        loadItems()
        loadCategories()
    }

    private fun loadItems() {
        viewModelScope.launch {
            combine(
                itemRepository.observeActiveItems(),
                _currentFilter,
                _currentSort,
                _selectedCategory
            ) { items, filter, sort, category ->
                val today = clock.today()

                val filtered = when (filter) {
                    ProductFilter.ALL -> items
                    ProductFilter.EXPIRING_SOON -> items.filter {
                        it.daysUntilExpiry(today) in 0..7
                    }
                    ProductFilter.EXPIRED -> items.filter { it.isExpired(today) }
                    ProductFilter.BY_CATEGORY ->
                        category?.let { cat -> items.filter { it.category == cat } } ?: items
                }

                when (sort) {
                    ProductSort.EXPIRY_DATE_ASC -> filtered.sortedBy { it.expiry.value }
                    ProductSort.EXPIRY_DATE_DESC -> filtered.sortedByDescending { it.expiry.value }
                    ProductSort.NAME_ASC -> filtered.sortedBy { it.name }
                    ProductSort.NAME_DESC -> filtered.sortedByDescending { it.name }
                    ProductSort.ADDED_DATE_DESC -> filtered.sortedByDescending { it.addedAt }
                }
            }.collect { items ->
                _uiState.update { it.copy(items = items, isLoading = false) }
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories) }
            }
        }
    }

    fun setFilter(filter: ProductFilter) {
        _currentFilter.value = filter
    }

    fun setSort(sort: ProductSort) {
        _currentSort.value = sort
    }

    fun selectCategory(category: String?) {
        _selectedCategory.value = category
        _uiState.update { it.copy(selectedCategory = category) }
        if (category != null) {
            _currentFilter.value = ProductFilter.BY_CATEGORY
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch { itemRepository.delete(itemId) }
    }

    fun markAsUsed(itemId: String) {
        viewModelScope.launch { itemRepository.use(itemId) }
    }

    fun markAsDiscarded(itemId: String) {
        viewModelScope.launch { itemRepository.discard(itemId) }
    }
}

data class ItemListUiState(
    val items: List<Item> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)
