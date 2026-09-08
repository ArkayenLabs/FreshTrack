package com.example.freshtrack.presentation.screen.auth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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

/**
 * These previously asserted on labels the screen has never had — "FreshTrack
 * Login", "Sign in with Phone", "Register" — so all three failed the first time
 * anything actually ran them. They now assert what the screen renders.
 *
 * Backed by a real Activity because the auth and dashboard screens reach for an
 * Activity context; a bare compose rule leaves setContent with no hierarchy to
 * attach to.
 */
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun setContent(viewModel: AuthViewModel) {
        composeTestRule.setContent {
            LoginScreen(
                viewModel = viewModel,
                onNavigateToRegister = {},
                onNavigateToForgotPassword = {},
                onLoginSuccess = {}
            )
        }
    }

    private fun idleViewModel(): AuthViewModel {
        val viewModel = mockk<AuthViewModel>(relaxed = true)
        every { viewModel.uiState } returns MutableStateFlow(AuthUiState())
        return viewModel
    }

    @Test
    fun loginScreen_rendersCorrectly() {
        setContent(idleViewModel())

        composeTestRule.onNodeWithText("Welcome Back").assertExists()
        composeTestRule.onNodeWithText("Sign in to continue").assertExists()
        composeTestRule.onNodeWithText("Email").assertExists()
        composeTestRule.onNodeWithText("Password").assertExists()
        composeTestRule.onNodeWithText("Sign In").assertExists()
        composeTestRule.onNodeWithText("Continue with Google").assertExists()
        composeTestRule.onNodeWithText("Sign Up").assertExists()
    }

    @Test
    fun loginScreen_offersAGuestRouteSoSignInIsNeverForced() {
        // Deferred auth is a product decision, not a nicety: a user must be able
        // to reach the app without an account.
        setContent(idleViewModel())

        composeTestRule.onNodeWithText("Continue without account").assertExists()
    }

    @Test
    fun loginScreen_buttonDisabledWhenFieldsEmpty() {
        val viewModel = idleViewModel()
        setContent(viewModel)

        composeTestRule.onNodeWithText("Sign In").assertIsNotEnabled()
        composeTestRule.onNodeWithText("Sign In").performClick()

        verify(exactly = 0) { viewModel.signInWithEmail(any(), any()) }
    }

    @Test
    fun loginScreen_buttonCallsViewModelWhenFieldsFilled() {
        val viewModel = idleViewModel()
        setContent(viewModel)

        composeTestRule.onNodeWithText("Email").performTextInput("test@test.com")
        composeTestRule.onNodeWithText("Password").performTextInput("password")
        composeTestRule.onNodeWithText("Sign In").performClick()

        verify(exactly = 1) { viewModel.signInWithEmail("test@test.com", "password") }
    }
}
