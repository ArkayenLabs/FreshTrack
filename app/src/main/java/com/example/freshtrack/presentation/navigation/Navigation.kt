package com.example.freshtrack.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
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
    object Premium : Screen("premium")

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

/** Somewhere a launch wants to land other than Today. See MainActivity.launchTarget. */
sealed interface LaunchTarget {
    data class OpenItem(val itemId: String) : LaunchTarget
    data object AddItem : LaunchTarget
    data object Expiring : LaunchTarget
}

/**
 * Main navigation graph for FreshTrack
 */
@Composable
fun FreshTrackNavGraph(
    navController: NavHostController,
    launchTarget: LaunchTarget? = null,
    onLaunchTargetConsumed: () -> Unit = {},
    onboardingPreferences: OnboardingPreferences = koinInject(),
    itemRepository: com.example.freshtrack.data.repository.ItemRepository = koinInject()
) {
    val scannerState = remember { ScannerState() }
    val appScope = rememberCoroutineScope()

    // A widget or notification asked for somewhere specific. Once the app is
    // past the splash, go there on top of Today so back lands somewhere
    // sensible. The splash handler below does the same for a cold start.
    fun goTo(target: LaunchTarget) {
        when (target) {
            is LaunchTarget.OpenItem -> navController.navigate(Screen.ProductDetails.createRoute(target.itemId))
            LaunchTarget.AddItem -> {
                scannerState.clear()
                navController.navigate(Screen.AddProduct.route)
            }
            LaunchTarget.Expiring -> navController.navigate(Screen.ProductList.createRoute("expiring"))
        }
        onLaunchTargetConsumed()
    }
    LaunchedEffect(launchTarget) {
        val target = launchTarget ?: return@LaunchedEffect
        val route = navController.currentBackStackEntry?.destination?.route
        if (route != null && route != Screen.Splash.route) goTo(target)
    }

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
                    if (nextDestination == Screen.Today.route) launchTarget?.let { goTo(it) }
                }
            )
        }

        // ─── Onboarding ───────────────────────────────────────────────────────────
        composable(Screen.Onboarding.route) {
            // Finishing or skipping the intro offers sign-in once, with
            // "continue without an account" right there. Nothing forces it;
            // the choice is just made visible instead of buried in Settings.
            //
            // Until Premium is on sale an account buys nothing, so the offer
            // is not made: onboarding lands on Today as a guest and the
            // sign-in screen returns, with its promise true, when the flag
            // flips. Same gate as the paywall.
            val toSignIn = {
                onboardingPreferences.setOnboardingCompleted()
                if (com.example.freshtrack.data.billing.PREMIUM_ON_SALE) {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding.route) { inclusive = true }
                    }
                } else {
                    onboardingPreferences.setGuestMode(true)
                    navController.navigate(Screen.Today.route) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            }
            OnboardingScreen(onComplete = toSignIn, onSkip = toSignIn)
        }

        // ─── Login ────────────────────────────────────────────────────────────────
        composable(Screen.Login.route) {
            // Whichever way the person leaves — signed in or not — Today
            // becomes the root again. Reached from Settings the stack is
            // Today, Settings, Login; reached after onboarding it is just
            // Login. Clearing to the graph root handles both without a
            // second copy of Today underneath.
            val today: () -> Unit = {
                navController.navigate(Screen.Today.route) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
            }
            LoginScreen(
                onNavigateBack = if (navController.previousBackStackEntry != null) {
                    { navController.navigateUp() }
                } else null,
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onNavigateToForgotPassword = {
                    navController.navigate(Screen.ForgotPassword.route)
                },
                onLoginSuccess = {
                    claimLocalData()
                    onboardingPreferences.setGuestMode(false)
                    today()
                },
                onContinueAsGuest = {
                    onboardingPreferences.setGuestMode(true)
                    today()
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
                        popUpTo(navController.graph.id) { inclusive = true }
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
                onNavigateToScanner = {
                    scannerState.clear()
                    navController.navigate(Screen.AddProduct.route)
                    navController.navigate(Screen.Scanner.createRoute(ScanMode.BARCODE))
                },
                onNavigateToReceipt = { navController.navigate(Screen.Receipt.route) },
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
                // Straight to the camera, with the Add screen underneath it
                // so the barcode has somewhere to land.
                onNavigateToScanner = {
                    scannerState.clear()
                    navController.navigate(Screen.AddProduct.route)
                    navController.navigate(Screen.Scanner.createRoute(ScanMode.BARCODE))
                },
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
                onNavigateToPremium = { navController.navigate(Screen.Premium.route) },
                // A plain push: back from the sign-in screen returns here.
                onSignIn = { navController.navigate(Screen.Login.route) },
                onSignOut = {
                    // Clear guest mode flag so they land on Login, not Today
                    onboardingPreferences.setGuestMode(false)
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Today.route) { inclusive = true }
                    }
                }
            )
        }

        // ─── Premium ──────────────────────────────────────────────────────────────
        composable(Screen.Premium.route) {
            com.example.freshtrack.presentation.screen.premium.PremiumScreen(
                onNavigateBack = { navController.navigateUp() },
                onNavigateToLogin = { navController.navigate(Screen.Login.route) }
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
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToToday = { navigateToTab(TopLevelDestination.TODAY) },
                onNavigateToAddItem = {
                    scannerState.clear()
                    navController.navigate(Screen.AddProduct.route)
                },
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