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
import com.example.freshtrack.presentation.screen.today.TodayScreen
import com.example.freshtrack.presentation.screen.productlist.ProductListScreen
import com.example.freshtrack.presentation.screen.addproduct.AddEditProductScreen
import com.example.freshtrack.presentation.screen.licenses.CustomOSSLicensesScreen
import com.example.freshtrack.presentation.screen.productdetails.ProductDetailsScreen
import com.example.freshtrack.presentation.screen.settings.SettingsScreen
import com.example.freshtrack.domain.model.ExpiryDate
import com.example.freshtrack.presentation.screen.receipt.ReceiptCaptureScreen
import com.example.freshtrack.presentation.screen.scanner.BarcodeScannerScreen
import com.example.freshtrack.presentation.screen.scanner.ScanMode
import com.example.freshtrack.presentation.screen.onboarding.OnboardingScreen
import com.example.freshtrack.presentation.screen.splash.SplashScreen
import com.example.freshtrack.presentation.screen.history.HistoryScreen
import com.example.freshtrack.presentation.screen.impact.ImpactScreen
import com.example.freshtrack.presentation.viewmodel.AuthViewModel
import com.google.firebase.auth.FirebaseAuth
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import com.example.freshtrack.presentation.theme.Motion

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
    object Today : Screen("today")
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
    object Scanner : Screen("scanner?mode={mode}") {
        fun createRoute(mode: ScanMode) = "scanner?mode=${mode.name}"
    }
    object History : Screen("history")
    object Impact : Screen("impact")

    /**
     * Capture and review are one destination, not two.
     *
     * A reviewed sheet is far too much state to hand across a route argument,
     * and "photograph it again" is a step back inside the flow rather than a
     * new journey into it.
     */
    object Receipt : Screen("receipt")
}

/**
 * Shared state for barcode scanning result
 */
class ScannerState {
    var scannedBarcode by mutableStateOf<String?>(null)
        private set

    /** A date read off a packet and confirmed by the user in the review sheet. */
    var scannedExpiry by mutableStateOf<ExpiryDate?>(null)
        private set

    fun setBarcode(barcode: String) {
        scannedBarcode = barcode
    }

    fun setExpiry(expiry: ExpiryDate) {
        scannedExpiry = expiry
    }

    fun clear() {
        scannedBarcode = null
        scannedExpiry = null
    }
}

/**
 * Main navigation graph for FreshTrack
 */
@Composable
fun FreshTrackNavGraph(
    navController: NavHostController,
    onboardingPreferences: OnboardingPreferences = koinInject(),
    itemRepository: com.example.freshtrack.data.repository.ItemRepository = koinInject()
) {
    val scannerState = remember { ScannerState() }
    val appScope = rememberCoroutineScope()

    // Anything added as a guest is adopted by the account on sign-in, so weeks
    // of tracking are not stranded the moment someone finally signs up.
    fun claimLocalData() {
        appScope.launch { runCatching { itemRepository.claimLocalData() } }
    }

    // Tabs are places, not steps. Switching between them must not grow the back
    // stack, and returning to one should find it as it was left — hence saveState
    // and restoreState rather than a plain navigate.
    fun navigateToTab(destination: TopLevelDestination) {
        navController.navigate(destination.navRoute) {
            popUpTo(Screen.Today.route) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun bottomBarFor(current: TopLevelDestination): @Composable () -> Unit = {
        GoodBeforeBottomBar(
            currentRoute = current.matchRoute,
            onNavigate = ::navigateToTab
        )
    }

    // Every screen moves the same way. Deeper is a nudge in from the right
    // with a fade; back is the reverse; a tab switch is a crossfade.
    val topLevel = remember { TopLevelDestination.entries.map { it.matchRoute }.toSet() }

    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route,
        enterTransition = { Motion.enter(this, topLevel) },
        exitTransition = { Motion.exit(this, topLevel) },
        popEnterTransition = { Motion.popEnter(this, topLevel) },
        popExitTransition = { Motion.popExit(this, topLevel) }
    ) {

        // ─── Splash Screen ────────────────────────────────────────────────────────
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashComplete = {
                    val isLoggedIn = FirebaseAuth.getInstance().currentUser != null
                    val isGuest = onboardingPreferences.isGuestMode()
                    val onboardingDone = onboardingPreferences.isOnboardingCompleted()

                    // Deferred auth: never force Login at startup. A user who has
                    // finished onboarding but not signed in is a guest by default;
                    // they reach Login only when they tap a cloud feature.
                    val nextDestination = when {
                        isLoggedIn -> Screen.Today.route
                        isGuest -> Screen.Today.route
                        !onboardingDone -> Screen.Onboarding.route
                        else -> Screen.Today.route
                    }

                    // Make the guest state explicit for anyone landing on
                    // Today without an account, so later launches route the
                    // same way and the guest banner shows correctly.
                    if (!isLoggedIn && nextDestination == Screen.Today.route) {
                        onboardingPreferences.setGuestMode(true)
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
                // Both finishing and skipping the intro drop the user into the
                // app as a guest. Neither forces a login; that only happens later
                // when they choose a cloud feature.
                onComplete = {
                    onboardingPreferences.setOnboardingCompleted()
                    onboardingPreferences.setGuestMode(true)
                    navController.navigate(Screen.Today.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                },
                onSkip = {
                    onboardingPreferences.setOnboardingCompleted()
                    onboardingPreferences.setGuestMode(true)
                    navController.navigate(Screen.Today.route) {
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
                    claimLocalData()
                    onboardingPreferences.setGuestMode(false)
                    navController.navigate(Screen.Today.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                onContinueAsGuest = {
                    onboardingPreferences.setGuestMode(true)
                    navController.navigate(Screen.Today.route) {
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
                    claimLocalData()
                    onboardingPreferences.setGuestMode(false)
                    navController.navigate(Screen.Today.route) {
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

        // ─── Today ────────────────────────────────────────────────────────────────
        composable(Screen.Today.route) {
            TodayScreen(
                onNavigateToAddItem = {
                    scannerState.clear()
                    navController.navigate(Screen.AddProduct.route)
                },
                onNavigateToItemDetails = { itemId ->
                    navController.navigate(Screen.ProductDetails.createRoute(itemId))
                },
                onNavigateToKitchen = { navigateToTab(TopLevelDestination.KITCHEN) },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                },
                bottomBar = bottomBarFor(TopLevelDestination.TODAY)
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
                onNavigateToAddProduct = {
                    scannerState.clear()
                    navController.navigate(Screen.AddProduct.route)
                },
                onNavigateToProductDetails = { productId ->
                    navController.navigate(Screen.ProductDetails.createRoute(productId))
                },
                onNavigateToReceipt = { navController.navigate(Screen.Receipt.route) },
                initialFilter = filter,
                bottomBar = bottomBarFor(TopLevelDestination.KITCHEN)
            )
        }

        // ─── Receipt capture and review ───────────────────────────────────────────
        composable(Screen.Receipt.route) {
            ReceiptCaptureScreen(
                onNavigateBack = { navController.navigateUp() }
            )
        }

        // ─── Add Product ──────────────────────────────────────────────────────────
        composable(Screen.AddProduct.route) {
            AddEditProductScreen(
                productId = null,
                scannedBarcode = scannerState.scannedBarcode,
                scannedExpiry = scannerState.scannedExpiry,
                onNavigateBack = {
                    scannerState.clear()
                    navController.navigateUp()
                },
                onNavigateToScanner = {
                    navController.navigate(Screen.Scanner.createRoute(ScanMode.BARCODE))
                },
                onNavigateToDateScanner = {
                    navController.navigate(Screen.Scanner.createRoute(ScanMode.DATE))
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
                scannedExpiry = scannerState.scannedExpiry,
                onNavigateBack = {
                    scannerState.clear()
                    navController.navigateUp()
                },
                onNavigateToScanner = {
                    navController.navigate(Screen.Scanner.createRoute(ScanMode.BARCODE))
                },
                onNavigateToDateScanner = {
                    navController.navigate(Screen.Scanner.createRoute(ScanMode.DATE))
                }
            )
        }

        // ─── Settings ─────────────────────────────────────────────────────────────
        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToLicenses = { navController.navigate(Screen.OpenSourceLicenses.route) },
                onNavigateToHistory = { navController.navigate(Screen.History.route) },
                onSignOut = {
                    // Clear guest mode flag so they land on Login, not Today
                    onboardingPreferences.setGuestMode(false)
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Today.route) { inclusive = true }
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

        // ─── Progress ─────────────────────────────────────────────────────────────
        composable(Screen.Impact.route) {
            ImpactScreen(
                bottomBar = bottomBarFor(TopLevelDestination.PROGRESS)
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
        composable(
            route = Screen.Scanner.route,
            arguments = listOf(
                navArgument("mode") {
                    type = NavType.StringType
                    defaultValue = ScanMode.BARCODE.name
                }
            )
        ) { backStackEntry ->
            val mode = backStackEntry.arguments?.getString("mode")
                ?.let { runCatching { ScanMode.valueOf(it) }.getOrNull() }
                ?: ScanMode.BARCODE

            BarcodeScannerScreen(
                mode = mode,
                onBarcodeScanned = { barcode ->
                    scannerState.setBarcode(barcode)
                    navController.navigateUp()
                },
                onDateScanned = { expiry ->
                    scannerState.setExpiry(expiry)
                    navController.navigateUp()
                },
                onNavigateBack = { navController.navigateUp() }
            )
        }
    }
}