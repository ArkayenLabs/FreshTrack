package com.example.freshtrack.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshtrack.data.repository.CategoryRepository
import com.example.freshtrack.data.repository.ProductRepository
import com.example.freshtrack.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for Dashboard Screen
 * Manages overview of expiring items and quick stats
 */
class DashboardViewModel(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    private fun loadDashboardData() {
        viewModelScope.launch {
            // Combine multiple flows for dashboard overview
            combine(
                productRepository.getAllProducts(),
                productRepository.getExpiredProducts(),
                productRepository.getActiveProductCount()
            ) { allProducts, expiredProducts, activeCount ->

                val expiringToday = allProducts.filter { it.daysUntilExpiry() == 0L }
                val expiringThisWeek = allProducts.filter {
                    it.daysUntilExpiry() in 1..7
                }
                val criticalItems = allProducts.filter {
                    it.getUrgency() == ExpiryUrgency.CRITICAL
                }

                DashboardUiState(
                    totalActiveProducts = activeCount,
                    expiringToday = expiringToday,
                    expiringThisWeek = expiringThisWeek,
                    expiredProducts = expiredProducts,
                    criticalItems = criticalItems,
                    isLoading = false
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    fun markAsConsumed(productId: String) {
        viewModelScope.launch {
            productRepository.markAsConsumed(productId)
        }
    }

    fun markAsDiscarded(productId: String) {
        viewModelScope.launch {
            productRepository.markAsDiscarded(productId)
        }
    }
}

/**
 * UI State for Dashboard
 */
data class DashboardUiState(
    val totalActiveProducts: Int = 0,
    val expiringToday: List<Product> = emptyList(),
    val expiringThisWeek: List<Product> = emptyList(),
    val expiredProducts: List<Product> = emptyList(),
    val criticalItems: List<Product> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

/**
 * ViewModel for Product List Screen
 * Handles filtering, sorting, and product list operations
 */
class ProductListViewModel(
    private val productRepository: ProductRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow(ProductListUiState())
    val uiState: StateFlow<ProductListUiState> = _uiState.asStateFlow()

    // Filter and sort settings
    private val _currentFilter = MutableStateFlow(ProductFilter.ALL)
    private val _currentSort = MutableStateFlow(ProductSort.EXPIRY_DATE_ASC)
    private val _selectedCategory = MutableStateFlow<String?>(null)

    init {
        loadProducts()
        loadCategories()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            combine(
                productRepository.getAllProducts(),
                _currentFilter,
                _currentSort,
                _selectedCategory
            ) { products, filter, sort, category ->

                // Apply filters
                var filteredProducts = when (filter) {
                    ProductFilter.ALL -> products
                    ProductFilter.EXPIRING_SOON -> products.filter {
                        it.daysUntilExpiry() in 0..7
                    }
                    ProductFilter.EXPIRED -> products.filter { it.isExpired() }
                    ProductFilter.BY_CATEGORY -> {
                        category?.let { cat ->
                            products.filter { it.category == cat }
                        } ?: products
                    }
                }

                // Apply sorting
                filteredProducts = when (sort) {
                    ProductSort.EXPIRY_DATE_ASC -> filteredProducts.sortedBy { it.expiryDate }
                    ProductSort.EXPIRY_DATE_DESC -> filteredProducts.sortedByDescending { it.expiryDate }
                    ProductSort.NAME_ASC -> filteredProducts.sortedBy { it.name }
                    ProductSort.NAME_DESC -> filteredProducts.sortedByDescending { it.name }
                    ProductSort.ADDED_DATE_DESC -> filteredProducts.sortedByDescending { it.addedDate }
                }

                filteredProducts
            }.collect { products ->
                _uiState.update { it.copy(
                    products = products,
                    isLoading = false
                )}
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

    fun deleteProduct(productId: String) {
        viewModelScope.launch {
            productRepository.deleteProduct(productId)
        }
    }

    fun markAsConsumed(productId: String) {
        viewModelScope.launch {
            productRepository.markAsConsumed(productId)
        }
    }

    fun markAsDiscarded(productId: String) {
        viewModelScope.launch {
            productRepository.markAsDiscarded(productId)
        }
    }
}

/**
 * UI State for Product List
 */
data class ProductListUiState(
    val products: List<Product> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedCategory: String? = null, // <-- Add this line
    val isLoading: Boolean = true,
    val error: String? = null
)