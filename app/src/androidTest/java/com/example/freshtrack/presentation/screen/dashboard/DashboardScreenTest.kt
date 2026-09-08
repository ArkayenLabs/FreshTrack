package com.example.freshtrack.presentation.screen.dashboard

import android.Manifest
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.freshtrack.presentation.viewmodel.DashboardUiState
import com.example.freshtrack.presentation.viewmodel.DashboardViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test

class DashboardScreenTest {

    @get:Rule
    // Activity-backed: DashboardScreen requests the notification permission,
    // which needs a real Activity. With a bare compose rule setContent
    // produced no hierarchy at all and every assertion failed.
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    /**
     * Pre-granted so the screen's permission request never opens a system
     * dialog. That dialog takes the window, which is why these assertions
     * could not find the app's compose hierarchy at all.
     */
    @get:Rule
    val permissionRule: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            GrantPermissionRule.grant()
        }

    @Test
    fun dashboardScreen_emptyState_rendersCorrectly() {
        val mockViewModel = mockk<DashboardViewModel>(relaxed = true)
        val uiStateFlow = MutableStateFlow(
            DashboardUiState(
                isLoading = false,
                totalActiveItems = 0,
                expiringToday = emptyList(),
                expiringThisWeek = emptyList(),
                expiredItems = emptyList(),
                safeItems = emptyList()
            )
        )
        every { mockViewModel.uiState } returns uiStateFlow

        composeTestRule.setContent {
            DashboardScreen(
                onNavigateToProductList = {},
                onNavigateToExpiringProducts = {},
                onNavigateToAddProduct = {},
                onNavigateToProductDetails = {},
                onNavigateToSettings = {},
                viewModel = mockViewModel
            )
        }

        // Verify empty state text
        composeTestRule.onNodeWithText("FreshTrack").assertExists()
        composeTestRule.onNodeWithText("Start Tracking").assertExists()
        composeTestRule.onNodeWithText("Add your first product to reduce waste").assertExists()
    }

    @Test
    fun dashboardScreen_emptyState_addClickWorks() {
        val mockViewModel = mockk<DashboardViewModel>(relaxed = true)
        every { mockViewModel.uiState } returns MutableStateFlow(
            // isLoading must be set: it defaults to true, and the screen then
            // renders the spinner rather than the empty state this asserts on.
            DashboardUiState(totalActiveItems = 0, isLoading = false)
        )

        var addClicked = false

        composeTestRule.setContent {
            DashboardScreen(
                onNavigateToProductList = {},
                onNavigateToExpiringProducts = {},
                onNavigateToAddProduct = { addClicked = true },
                onNavigateToProductDetails = {},
                onNavigateToSettings = {},
                viewModel = mockViewModel
            )
        }

        composeTestRule.onNodeWithText("Add Product").performClick()
        assert(addClicked)
    }
}
