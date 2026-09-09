package com.example.freshtrack.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshtrack.domain.repository.AuthRepository
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: AuthError? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    val isUserLoggedIn = authRepository.isUserLoggedIn

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = authRepository.signInWithEmail(email, password)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = friendlyAuthError(e)) }
            }
        }
    }

    fun registerWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = authRepository.registerWithEmail(email, password)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = friendlyAuthError(e)) }
            }
        }
    }

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val result = authRepository.signInWithGoogle(idToken)
            result.onSuccess {
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isLoading = false, error = friendlyAuthError(e)) }
            }
        }
    }

    fun sendPasswordResetEmail(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null, isSuccess = false) }
            val result = authRepository.sendPasswordResetEmail(email)
            // Always reports success, even when no account exists for the address.
            // Reporting "no such user" here would turn this screen into a way to
            // test which emails are registered. Network errors still surface, since
            // those say nothing about the account.
            result.onFailure { e ->
                if (e is FirebaseNetworkException) {
                    _uiState.update { it.copy(isLoading = false, error = friendlyAuthError(e)) }
                    return@launch
                }
            }
            _uiState.update { it.copy(isLoading = false, isSuccess = true) }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            _uiState.update { AuthUiState() } // Reset state
        }
    }

    /**
     * Maps auth failures to messages that are safe to show.
     *
     * Sign-in failures deliberately do not distinguish "no such account" from
     * "wrong password" — telling them apart lets anyone probe which email
     * addresses are registered.
     */
    private fun friendlyAuthError(e: Throwable): AuthError = when (e) {
        // Must be checked before FirebaseAuthInvalidCredentialsException, which it
        // extends — otherwise a weak password reports as a wrong password.
        is FirebaseAuthWeakPasswordException -> AuthError.WEAK_PASSWORD

        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> AuthError.INVALID_CREDENTIALS

        is FirebaseAuthUserCollisionException -> AuthError.EMAIL_IN_USE

        is FirebaseNetworkException -> AuthError.NO_NETWORK

        is FirebaseTooManyRequestsException -> AuthError.TOO_MANY_ATTEMPTS

        else -> AuthError.GENERIC
    }

    fun resetState() {
        _uiState.update { AuthUiState() }
    }
}

/**
 * Why signing in failed, as a fact rather than a sentence.
 *
 * Note what is missing: there is no "no account for that email". Telling a
 * wrong password apart from an unknown address lets anyone probe which
 * addresses are registered, so both arrive here as INVALID_CREDENTIALS.
 */
enum class AuthError {
    WEAK_PASSWORD,
    INVALID_CREDENTIALS,
    EMAIL_IN_USE,
    NO_NETWORK,
    TOO_MANY_ATTEMPTS,
    GENERIC
}
