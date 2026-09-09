package com.example.freshtrack.presentation.screen.auth

import androidx.annotation.StringRes
import com.example.freshtrack.R
import com.example.freshtrack.presentation.viewmodel.AuthError

/**
 * The words for a sign-in failure the ViewModel reported as a fact.
 *
 * Shared by the three auth screens so the same failure cannot end up phrased
 * two different ways depending on which screen the user was on.
 */
@StringRes
internal fun messageFor(error: AuthError): Int = when (error) {
    AuthError.WEAK_PASSWORD -> R.string.auth_error_weak_password
    AuthError.INVALID_CREDENTIALS -> R.string.auth_error_invalid_credentials
    AuthError.EMAIL_IN_USE -> R.string.auth_error_email_in_use
    AuthError.NO_NETWORK -> R.string.auth_error_no_network
    AuthError.TOO_MANY_ATTEMPTS -> R.string.auth_error_too_many_attempts
    AuthError.GENERIC -> R.string.auth_error_generic
}
