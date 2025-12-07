package com.example.freshtrack.presentation.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.freshtrack.presentation.component.*
import com.example.freshtrack.presentation.theme.*
import com.example.freshtrack.presentation.viewmodel.DashboardViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToProductList: () -> Unit,
    onNavigateToAddProduct: () -> Unit,
    onNavigateToProductDetails: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Eco,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                        Text(
                            "FreshTrack",
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddProduct,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Product"
                    )
                },
                text = { Text("Add") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LoadingState(message = "Loading products...")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Statistics Cards (60% primary color usage)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            title = "Products",
                            value = uiState.totalActiveProducts.toString(),
                            icon = Icons.Default.Inventory2,
                            backgroundColor = MaterialTheme.colorScheme.primary,
                            onClick = onNavigateToProductList,
                            modifier = Modifier.weight(1f)
                        )

                        StatCard(
                            title = "Expiring",
                            value = (uiState.expiringToday.size + uiState.expiringThisWeek.size).toString(),
                            icon = Icons.Default.Warning,
                            backgroundColor = UrgencyWarning,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Critical Items Section
                if (uiState.expiringToday.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Expiring Today",
                            icon = Icons.Default.Error,
                            color = UrgencyCritical,
                            count = uiState.expiringToday.size
                        )
                    }

                    items(uiState.expiringToday) { product ->
                        ProductCard(
                            product = product,
                            onClick = { onNavigateToProductDetails(product.id) }
                        )
                    }
                }

                // Expiring This Week
                if (uiState.expiringThisWeek.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "This Week",
                            icon = Icons.Default.CalendarToday,
                            color = UrgencyWarning,
                            count = uiState.expiringThisWeek.size
                        )
                    }

                    items(uiState.expiringThisWeek.take(5)) { product ->
                        ProductCard(
                            product = product,
                            onClick = { onNavigateToProductDetails(product.id) }
                        )
                    }
                }

                // Expired Products
                if (uiState.expiredProducts.isNotEmpty()) {
                    item {
                        SectionHeader(
                            title = "Expired",
                            icon = Icons.Default.Block,
                            color = UrgencyExpired,
                            count = uiState.expiredProducts.size
                        )
                    }

                    items(uiState.expiredProducts.take(3)) { product ->
                        ProductCard(
                            product = product,
                            onClick = { onNavigateToProductDetails(product.id) }
                        )
                    }
                }

                // Empty State
                if (uiState.totalActiveProducts == 0) {
                    item {
                        EmptyState(
                            title = "Start Tracking",
                            message = "Add your first product to reduce food waste",
                            icon = {
                                Icon(
                                    Icons.Default.Eco,
                                    contentDescription = null,
                                    modifier = Modifier.size(80.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                )
                            },
                            actionButton = {
                                FilledTonalButton(
                                    onClick = onNavigateToAddProduct,
                                    modifier = Modifier.fillMaxWidth(0.6f)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Add Product")
                                }
                            }
                        )
                    }
                }

                // View All Button
                if (uiState.totalActiveProducts > 0) {
                    item {
                        OutlinedButton(
                            onClick = onNavigateToProductList,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("View All Products")
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Default.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: androidx.compose.ui.graphics.Color,
    count: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color.copy(alpha = 0.15f),
                    shape = androidx.compose.foundation.shape.CircleShape
                ),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )

        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = color.copy(alpha = 0.15f)
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}