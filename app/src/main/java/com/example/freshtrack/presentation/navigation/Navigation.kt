package com.example.freshtrack.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.freshtrack.data.preferences.OnboardingPreferences
import com.example.freshtrack.presentation.screen.dashboard.DashboardScreen
import com.example.freshtrack.presentation.screen.productlist.ProductListScreen
import com.example.freshtrack.presentation.screen.addproduct.AddEditProductScreen
import com.example.freshtrack.presentation.screen.licenses.CustomOSSLicensesScreen
import com.example.freshtrack.presentation.screen.productdetails.ProductDetailsScreen
import com.example.freshtrack.presentation.screen.settings.SettingsScreen
import com.example.freshtrack.presentation.screen.scanner.BarcodeScannerScreen
import com.example.freshtrack.presentation.screen.onboarding.OnboardingScreen
import com.example.freshtrack.presentation.screen.splash.SplashScreen
import org.koin.compose.koinInject

/**
 * Navigation routes for the app
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Dashboard : Screen("dashboard")
    object ProductList : Screen("product_list")
    object AddProduct : Screen("add_product")
    object OpenSourceLicenses : Screen("oss_licenses")

    object EditProduct : Screen("edit_product/{productId}") {
        fun createRoute(productId: String) = "edit_product/$productId"
    }
    object ProductDetails : Screen("product_details/{productId}") {
        fun createRoute(productId: String) = "product_details/$productId"
    }
    object Settings : Screen("settings")
    object BarcodeScanner : Screen("barcode_scanner")
}

/**
 * Shared state for barcode scanning result
 */
class ScannerState {
    var scannedBarcode by mutableStateOf<String?>(null)
        private set

    fun setBarcode(barcode: String) {
        android.util.Log.d("ScannerState", "Setting barcode: $barcode")
        scannedBarcode = barcode
        android.util.Log.d("ScannerState", "Barcode set to: $scannedBarcode")
    }

    fun clear() {
        android.util.Log.d("ScannerState", "Clearing barcode. Was: $scannedBarcode")
        scannedBarcode = null
    }
}

/**
 * Main navigation graph for FreshTrack
 */
@Composable
fun FreshTrackNavGraph(
    navController: NavHostController,
    onboardingPreferences: OnboardingPreferences = koinInject()
) {


    // Shared scanner state
    val scannerState = remember { ScannerState() }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        // Onboarding Screen
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashComplete = {
                    val nextDestination = if(onboardingPreferences.isOnboardingCompleted()){
                        Screen.Dashboard.route
                    }else{
                        Screen.Onboarding.route
                    }

                    // Navigate to dashboard and remove onboarding from back stack
                    navController.navigate(nextDestination) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // Onboarding Screen
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    // Mark onboarding as completed
                    onboardingPreferences.setOnboardingCompleted()

                    // Navigate to dashboard and remove onboarding from back stack
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // Dashboard Screen
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToProductList = {
                    navController.navigate(Screen.ProductList.route)
                },
                onNavigateToAddProduct = {
                    scannerState.clear()
                    navController.navigate(Screen.AddProduct.route)
                },
                onNavigateToProductDetails = { productId ->
                    navController.navigate(Screen.ProductDetails.createRoute(productId))
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // Product List Screen
        composable(Screen.ProductList.route) {
            ProductListScreen(
                onNavigateBack = {
                    navController.navigateUp()
                },
                onNavigateToAddProduct = {
                    scannerState.clear()
                    navController.navigate(Screen.AddProduct.route)
                },
                onNavigateToProductDetails = { productId ->
                    navController.navigate(Screen.ProductDetails.createRoute(productId))
                }
            )
        }

        // Add Product Screen
        composable(Screen.AddProduct.route) {
            // Get scanned barcode if available
            val scannedBarcode = scannerState.scannedBarcode
            android.util.Log.d("Navigation", "AddProduct composable - scannedBarcode: $scannedBarcode")

            AddEditProductScreen(
                productId = null,
                scannedBarcode = scannedBarcode,
                onNavigateBack = {
                    scannerState.clear()
                    navController.navigateUp()
                },
                onNavigateToScanner = {
                    navController.navigate(Screen.BarcodeScanner.route)
                }
            )
        }

        // Edit Product Screen
        composable(
            route = Screen.EditProduct.route,
            arguments = listOf(
                navArgument("productId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId")
            // Get scanned barcode if available
            val scannedBarcode = scannerState.scannedBarcode

            AddEditProductScreen(
                productId = productId,
                scannedBarcode = scannedBarcode,
                onNavigateBack = {
                    scannerState.clear()
                    navController.navigateUp()
                },
                onNavigateToScanner = {
                    navController.navigate(Screen.BarcodeScanner.route)
                }
            )
        }
        // Settings Screen
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = {
                    navController.navigateUp()
                },
                onNavigateToLicenses = {
                    navController.navigate(Screen.OpenSourceLicenses.route)
                }
            )
        }
        composable(Screen.OpenSourceLicenses.route) {
            CustomOSSLicensesScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        // Product Details Screen
        composable(
            route = Screen.ProductDetails.route,
            arguments = listOf(
                navArgument("productId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId") ?: ""
            ProductDetailsScreen(
                productId = productId,
                onNavigateBack = {
                    navController.navigateUp()
                },
                onNavigateToEdit = { id ->
                    navController.navigate(Screen.EditProduct.createRoute(id))
                }
            )
        }



        // Barcode Scanner Screen
        composable(Screen.BarcodeScanner.route) {
            BarcodeScannerScreen(
                onBarcodeScanned = { barcode ->
                    android.util.Log.d("Navigation", "Barcode scanned callback: $barcode")
                    // Store barcode in shared state
                    scannerState.setBarcode(barcode)
                    android.util.Log.d("Navigation", "About to navigate back")
                    navController.navigateUp()
                },
                onNavigateBack = {
                    navController.navigateUp()
                }
            )
        }
    }
}