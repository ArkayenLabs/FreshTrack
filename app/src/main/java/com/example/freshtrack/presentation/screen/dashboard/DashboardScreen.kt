package com.example.freshtrack.presentation.screen.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.freshtrack.presentation.component.*
import com.example.freshtrack.presentation.theme.*
import com.example.freshtrack.presentation.viewmodel.DashboardViewModel
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToProductList: () -> Unit,
    onNavigateToExpiringProducts: () -> Unit,
    onNavigateToAddProduct: () -> Unit,
    onNavigateToProductDetails: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: DashboardViewModel = koinViewModel()
) {
    RequestNotificationPermissionSimple()
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
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
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingState(message = "Loading products...")
                }
            }

            uiState.totalActiveProducts == 0 -> {
                // Empty State - Separate layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    // Statistics Cards at top
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            title = "Products",
                            value = "0",
                            icon = Icons.Default.Inventory2,
                            backgroundColor = MaterialTheme.colorScheme.primary,
                            onClick = onNavigateToProductList,
                            modifier = Modifier.weight(1f)
                        )

                        StatCard(
                            title = "Expiring",
                            value = "0",
                            icon = Icons.Default.Warning,
                            backgroundColor = UrgencyWarning,
                            onClick = onNavigateToExpiringProducts,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Centered empty state content
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Eco,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "Start Tracking",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = "Add your first product to reduce waste",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }

                            Button(
                                onClick = onNavigateToAddProduct,
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Add Product",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            else -> {
                // Product List
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
                                onClick = onNavigateToExpiringProducts,
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

                        items(uiState.expiringThisWeek.take(6)) { product ->
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

                        items(uiState.expiredProducts.take(4)) { product ->
                            ProductCard(
                                product = product,
                                onClick = { onNavigateToProductDetails(product.id) }
                            )
                        }
                    }

                    // Safe Products (expiring later than 7 days)
                    if (uiState.safeProducts.isNotEmpty()) {
                        item {
                            SectionHeader(
                                title = "Later",
                                icon = Icons.Default.CheckCircle,
                                color = UrgencySafe,
                                count = uiState.safeProducts.size
                            )
                        }

                        items(uiState.safeProducts.take(4)) { product ->
                            ProductCard(
                                product = product,
                                onClick = { onNavigateToProductDetails(product.id) }
                            )
                        }
                    }

                    // View All Button
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
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color.copy(alpha = 0.15f),
                    shape = androidx.compose.foundation.shape.CircleShape
                ),
            contentAlignment = Alignment.Center
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