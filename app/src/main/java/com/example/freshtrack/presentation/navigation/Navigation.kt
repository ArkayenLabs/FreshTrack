package com.example.freshtrack.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.freshtrack.data.preferences.OnboardingPreferences
import com.example.freshtrack.presentation.screen.auth.ForgotPasswordScreen
import com.example.freshtrack.presentation.screen.auth.LoginScreen
import com.example.freshtrack.presentation.screen.auth.RegisterScreen
import com.example.freshtrack.presentation.screen.auth.TermsOfServiceScreen
import com.example.freshtrack.presentation.screen.dashboard.DashboardScreen
import com.example.freshtrack.presentation.screen.productlist.ProductListScreen
import com.example.freshtrack.presentation.screen.addproduct.AddEditProductScreen
import com.example.freshtrack.presentation.screen.licenses.CustomOSSLicensesScreen
import com.example.freshtrack.presentation.screen.productdetails.ProductDetailsScreen
import com.example.freshtrack.presentation.screen.settings.SettingsScreen
import com.example.freshtrack.presentation.screen.scanner.BarcodeScannerScreen
import com.example.freshtrack.presentation.screen.onboarding.OnboardingScreen
import com.example.freshtrack.presentation.screen.splash.SplashScreen
import com.example.freshtrack.presentation.screen.history.HistoryScreen
import com.example.freshtrack.presentation.screen.impact.ImpactScreen
import com.example.freshtrack.presentation.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * Navigation routes for the app
 */
sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Login : Screen("login")
    object Register : Screen("register")
    object ForgotPassword : Screen("forgot_password")
    object TermsOfService : Screen("terms_of_service")
    object Dashboard : Screen("dashboard")
    object ProductList : Screen("product_list?filter={filter}") {
        fun createRoute(filter: String? = null) = if (filter != null) "product_list?filter=$filter" else "product_list"
    }
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
    object History : Screen("history")
    object Impact : Screen("impact")
}

/**
 * Shared state for barcode scanning result
 */
class ScannerState {
    var scannedBarcode by mutableStateOf<String?>(null)
        private set

    fun setBarcode(barcode: String) {
        scannedBarcode = barcode
    }

    fun clear() {
        scannedBarcode = null
    }
}

/**
 * Main navigation graph for FreshTrack
 */
@Composable
fun FreshTrackNavGraph(
    navController: NavHostController,
    onboardingPreferences: OnboardingPreferences = koinInject(),
    productRepository: com.example.freshtrack.data.repository.ProductRepository = koinInject(),
    productSyncer: com.example.freshtrack.data.sync.ProductSyncer = koinInject()
) {
    val scannerState = remember { ScannerState() }
    val appScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Anything added as a guest is adopted by the account on sign-in, so weeks of
    // tracking are not stranded the moment someone finally signs up. Claim first,
    // then sync, so the newly claimed rows go up with everything else.
    fun claimGuestDataThenSync() {
        appScope.launch {
            runCatching { productRepository.claimGuestData() }
            com.example.freshtrack.data.sync.SyncWorker.syncNow(context)
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {

        // ─── Splash Screen ────────────────────────────────────────────────────────
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashComplete = {
                    val isLoggedIn = FirebaseAuth.getInstance().currentUser != null
                    val isGuest = onboardingPreferences.isGuestMode()
                    val onboardingDone = onboardingPreferences.isOnboardingCompleted()

                    val nextDestination = when {
                        isLoggedIn -> Screen.Dashboard.route
                        isGuest -> Screen.Dashboard.route
                        !onboardingDone -> Screen.Onboarding.route
                        else -> Screen.Login.route
                    }

                    navController.navigate(nextDestination) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        // ─── Onboarding ───────────────────────────────────────────────────────────
        composable(Screen.Onboarding.route) {
            OnboardingScreen(
                onComplete = {
                    onboardingPreferences.setOnboardingCompleted()
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                onSkip = {
                    onboardingPreferences.setOnboardingCompleted()
                    onboardingPreferences.setGuestMode(true)
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                }
            )
        }

        // ─── Login ────────────────────────────────────────────────────────────────
        composable(Screen.Login.route) {
            LoginScreen(
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onNavigateToForgotPassword = {
                    navController.navigate(Screen.ForgotPassword.route)
                },
                onLoginSuccess = {
                    claimGuestDataThenSync()
                    onboardingPreferences.setGuestMode(false)
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onContinueAsGuest = {
                    onboardingPreferences.setGuestMode(true)
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // ─── Forgot Password ──────────────────────────────────────────────────────
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        // ─── Register ─────────────────────────────────────────────────────────────
        composable(Screen.Register.route) {
            RegisterScreen(
                onNavigateToLogin = {
                    navController.navigateUp()
                },
                onNavigateToTerms = {
                    navController.navigate(Screen.TermsOfService.route)
                },
                onRegisterSuccess = {
                    claimGuestDataThenSync()
                    onboardingPreferences.setGuestMode(false)
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        // ─── Terms of Service ─────────────────────────────────────────────────────
        composable(Screen.TermsOfService.route) {
            TermsOfServiceScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        // ─── Dashboard ────────────────────────────────────────────────────────────
        composable(Screen.Dashboard.route) {
            DashboardScreen(
                onNavigateToProductList = {
                    navController.navigate(Screen.ProductList.createRoute())
                },
                onNavigateToExpiringProducts = {
                    navController.navigate(Screen.ProductList.createRoute("expiring"))
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

        // ─── Product List ─────────────────────────────────────────────────────────
        composable(
            route = Screen.ProductList.route,
            arguments = listOf(
                navArgument("filter") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val filter = backStackEntry.arguments?.getString("filter")
            ProductListScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToAddProduct = {
                    scannerState.clear()
                    navController.navigate(Screen.AddProduct.route)
                },
                onNavigateToProductDetails = { productId ->
                    navController.navigate(Screen.ProductDetails.createRoute(productId))
                },
                initialFilter = filter
            )
        }

        // ─── Add Product ──────────────────────────────────────────────────────────
        composable(Screen.AddProduct.route) {
            AddEditProductScreen(
                productId = null,
                scannedBarcode = scannerState.scannedBarcode,
                onNavigateBack = {
                    scannerState.clear()
                    navController.navigateUp()
                },
                onNavigateToScanner = {
                    navController.navigate(Screen.BarcodeScanner.route)
                }
            )
        }

        // ─── Edit Product ─────────────────────────────────────────────────────────
        composable(
            route = Screen.EditProduct.route,
            arguments = listOf(
                navArgument("productId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId")
            AddEditProductScreen(
                productId = productId,
                scannedBarcode = scannerState.scannedBarcode,
                onNavigateBack = {
                    scannerState.clear()
                    navController.navigateUp()
                },
                onNavigateToScanner = {
                    navController.navigate(Screen.BarcodeScanner.route)
                }
            )
        }

        // ─── Settings ─────────────────────────────────────────────────────────────
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToLicenses = { navController.navigate(Screen.OpenSourceLicenses.route) },
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
                onNavigateToImpact = { navController.navigate(Screen.Impact.route) },
                onSignOut = {
                    // Watermarks are per account; leaving them would make the
                    // next account think it had already synced up to this point.
                    productSyncer.onSignedOut()
                    // Clear guest mode flag so they land on Login, not Dashboard
                    onboardingPreferences.setGuestMode(false)
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Dashboard.route) { inclusive = true }
                    }
                }
            )
        }

        // ─── OSS Licenses ─────────────────────────────────────────────────────────
        composable(Screen.OpenSourceLicenses.route) {
            CustomOSSLicensesScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        // ─── History ──────────────────────────────────────────────────────────────
        composable(Screen.History.route) {
            HistoryScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        // ─── Impact Dashboard ─────────────────────────────────────────────────────
        composable(Screen.Impact.route) {
            ImpactScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        // ─── Product Details ──────────────────────────────────────────────────────
        composable(
            route = Screen.ProductDetails.route,
            arguments = listOf(
                navArgument("productId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val productId = backStackEntry.arguments?.getString("productId") ?: ""
            ProductDetailsScreen(
                productId = productId,
                onNavigateBack = { navController.navigateUp() },
                onNavigateToEdit = { id ->
                    navController.navigate(Screen.EditProduct.createRoute(id))
                }
            )
        }

        // ─── Barcode Scanner ──────────────────────────────────────────────────────
        composable(Screen.BarcodeScanner.route) {
            BarcodeScannerScreen(
                onBarcodeScanned = { barcode ->
                    scannerState.setBarcode(barcode)
                    navController.navigateUp()
                },
                onNavigateBack = { navController.navigateUp() }
            )
        }
    }
}