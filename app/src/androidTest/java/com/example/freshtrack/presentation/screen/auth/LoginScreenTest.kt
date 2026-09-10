package com.example.freshtrack.presentation.screen.auth

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.freshtrack.R
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
 * anything actually ran them. Then they asserted on literal English that the
 * i18n pass re-cased, and failed again. They now read the same resources the
 * screen does, so the copy can change without the test lying about it.
 *
 * Backed by a real Activity because the auth and dashboard screens reach for an
 * Activity context; a bare compose rule leaves setContent with no hierarchy to
 * attach to.
 */
class LoginScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private fun text(id: Int): String = composeTestRule.activity.getString(id)

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

        composeTestRule.onNodeWithText(text(R.string.auth_welcome_back)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.auth_sign_in_subtitle)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.auth_email)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.auth_password)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.settings_sign_in)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.auth_continue_google)).assertExists()
        composeTestRule.onNodeWithText(text(R.string.auth_sign_up)).assertExists()
    }

    @Test
    fun loginScreen_offersAGuestRouteSoSignInIsNeverForced() {
        // Deferred auth is a product decision, not a nicety: a user must be able
        // to reach the app without an account.
        setContent(idleViewModel())

        composeTestRule.onNodeWithText(text(R.string.auth_continue_without_account))
            .assertExists()
    }

    @Test
    fun loginScreen_buttonDisabledWhenFieldsEmpty() {
        val viewModel = idleViewModel()
        setContent(viewModel)

        composeTestRule.onNodeWithText(text(R.string.settings_sign_in)).assertIsNotEnabled()
        composeTestRule.onNodeWithText(text(R.string.settings_sign_in)).performClick()

        verify(exactly = 0) { viewModel.signInWithEmail(any(), any()) }
    }

    @Test
    fun loginScreen_buttonCallsViewModelWhenFieldsFilled() {
        val viewModel = idleViewModel()
        setContent(viewModel)

        composeTestRule.onNodeWithText(text(R.string.auth_email)).performTextInput("test@test.com")
        composeTestRule.onNodeWithText(text(R.string.auth_password)).performTextInput("password")
        composeTestRule.onNodeWithText(text(R.string.settings_sign_in)).performClick()

        verify(exactly = 1) { viewModel.signInWithEmail("test@test.com", "password") }
    }
}
