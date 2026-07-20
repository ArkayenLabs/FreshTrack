package com.example.freshtrack.presentation.screen.auth

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.freshtrack.presentation.viewmodel.AuthUiState
import com.example.freshtrack.presentation.viewmodel.AuthViewModel
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class LoginScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loginScreen_rendersCorrectly() {
        // Arrange
        val mockViewModel = mockk<AuthViewModel>(relaxed = true)
        every { mockViewModel.uiState } returns MutableStateFlow(AuthUiState())

        // Act
        composeTestRule.setContent {
            LoginScreen(
                viewModel = mockViewModel,
                onNavigateToRegister = {},
                onLoginSuccess = {}
            )
        }

        // Assert
        composeTestRule.onNodeWithText("FreshTrack Login").assertExists()
        composeTestRule.onNodeWithText("Email").assertExists()
        composeTestRule.onNodeWithText("Password").assertExists()
        composeTestRule.onNodeWithText("Login").assertExists()
        composeTestRule.onNodeWithText("Sign in with Google").assertExists()
        composeTestRule.onNodeWithText("Sign in with Phone").assertExists()
        composeTestRule.onNodeWithText("Don't have an account? Register").assertExists()
    }

    @Test
    fun loginScreen_buttonDisabledWhenFieldsEmpty() {
        val mockViewModel = mockk<AuthViewModel>(relaxed = true)
        every { mockViewModel.uiState } returns MutableStateFlow(AuthUiState())

        composeTestRule.setContent {
            LoginScreen(
                viewModel = mockViewModel,
                onNavigateToRegister = {},
                onLoginSuccess = {}
            )
        }

        // The button shouldn't be clickable or should be disabled
        // By checking if it exists but is disabled or by clicking and verifying no action
        composeTestRule.onNodeWithText("Login").performClick()
        verify(exactly = 0) { mockViewModel.signInWithEmail(any(), any()) }
    }

    @Test
    fun loginScreen_buttonCallsViewModelWhenFieldsFilled() {
        val mockViewModel = mockk<AuthViewModel>(relaxed = true)
        every { mockViewModel.uiState } returns MutableStateFlow(AuthUiState())

        composeTestRule.setContent {
            LoginScreen(
                viewModel = mockViewModel,
                onNavigateToRegister = {},
                onLoginSuccess = {}
            )
        }

        composeTestRule.onNodeWithText("Email").performTextInput("test@test.com")
        composeTestRule.onNodeWithText("Password").performTextInput("password")
        composeTestRule.onNodeWithText("Login").performClick()

        verify(exactly = 1) { mockViewModel.signInWithEmail("test@test.com", "password") }
    }
}
