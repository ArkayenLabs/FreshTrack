package com.example.freshtrack.presentation.screen.productlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.freshtrack.domain.model.ProductFilter
import com.example.freshtrack.domain.model.ProductSort
import com.example.freshtrack.presentation.component.*
import com.example.freshtrack.presentation.viewmodel.ProductListViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAddProduct: () -> Unit,
    onNavigateToProductDetails: (String) -> Unit,
    viewModel: ProductListViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showFilterMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Products") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Filter Button
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(Icons.Default.FilterList, "Filter")
                    }

                    // Sort Button
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.Sort, "Sort")
                    }

                    // Filter Menu
                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("All Products") },
                            onClick = {
                                viewModel.setFilter(ProductFilter.ALL)
                                viewModel.selectCategory(null)
                                showFilterMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Expiring Soon") },
                            onClick = {
                                viewModel.setFilter(ProductFilter.EXPIRING_SOON)
                                viewModel.selectCategory(null)
                                showFilterMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Expired") },
                            onClick = {
                                viewModel.setFilter(ProductFilter.EXPIRED)
                                viewModel.selectCategory(null)
                                showFilterMenu = false
                            }
                        )
                    }

                    // Sort Menu
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Expiry Date (Nearest First)") },
                            onClick = {
                                viewModel.setSort(ProductSort.EXPIRY_DATE_ASC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Name (A-Z)") },
                            onClick = {
                                viewModel.setSort(ProductSort.NAME_ASC)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Recently Added") },
                            onClick = {
                                viewModel.setSort(ProductSort.ADDED_DATE_DESC)
                                showSortMenu = false
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAddProduct) {
                Icon(Icons.Default.Add, "Add Product")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Category Filter Chips - ALWAYS VISIBLE with horizontal scroll
            if (uiState.categories.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(uiState.categories.size) { index ->
                        val category = uiState.categories[index]
                        FilterChip(
                            selected = false,
                            onClick = {
                                viewModel.selectCategory(category.name)
                            },
                            label = { Text(category.name) }
                        )
                    }
                }

                HorizontalDivider(thickness = 1.dp)
            }

            // Content Area
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LoadingState("Loading products...")
                }
            } else if (uiState.products.isEmpty()) {
                // Empty state - ALWAYS SHOW WITH ADD BUTTON
                Box(modifier = Modifier.fillMaxSize()) {
                    EmptyState(
                        title = "No Products Found",
                        message = "Add your first product to start tracking",
                        actionButton = {
                            Button(onClick = onNavigateToAddProduct) {
                                Icon(Icons.Default.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add Product")
                            }
                        }
                    )
                }
            } else {
                // Product List
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = uiState.products,
                        key = { it.id }
                    ) { product ->
                        ProductCard(
                            product = product,
                            onClick = { onNavigateToProductDetails(product.id) }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    showDeleteDialog?.let { productId ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Product") },
            text = { Text("Are you sure you want to delete this product?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProduct(productId)
                        showDeleteDialog = null
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}