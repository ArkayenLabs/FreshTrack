package com.example.freshtrack.presentation.screen.dashboard

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.freshtrack.presentation.viewmodel.DashboardUiState
import com.example.freshtrack.presentation.viewmodel.DashboardViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dashboardScreen_emptyState_rendersCorrectly() {
        val mockViewModel = mockk<DashboardViewModel>(relaxed = true)
        val uiStateFlow = MutableStateFlow(
            DashboardUiState(
                isLoading = false,
                totalActiveProducts = 0,
                expiringToday = emptyList(),
                expiringThisWeek = emptyList(),
                expiredProducts = emptyList(),
                safeProducts = emptyList()
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
            DashboardUiState(totalActiveProducts = 0)
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
