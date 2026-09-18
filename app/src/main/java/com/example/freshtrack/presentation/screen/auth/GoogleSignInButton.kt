package com.example.freshtrack.presentation.screen.auth

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.freshtrack.R
import com.example.freshtrack.presentation.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes

/**
 * One Google button for both auth screens, so a failure is reported the
 * same way on each. A failure used to be swallowed in a catch block, which
 * is how a missing SHA-1 fingerprint went unnoticed for weeks: Google
 * returns DEVELOPER_ERROR (10) and the screen simply did nothing.
 *
 * The user backing out of Google's sheet (12501) is not an error.
 */
@Composable
internal fun GoogleSignInButton(
    viewModel: AuthViewModel,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val webClientId = stringResource(R.string.default_web_client_id)

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK && result.data == null) return@rememberLauncherForActivityResult
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)
            val token = account.idToken
            if (token == null) {
                Log.w(TAG, "Google returned an account without an id token")
                viewModel.reportGoogleSignInFailure()
            } else {
                viewModel.signInWithGoogle(token)
            }
        } catch (e: ApiException) {
            if (e.statusCode == SIGN_IN_CANCELLED) return@rememberLauncherForActivityResult
            // 10 is DEVELOPER_ERROR: the app's signing fingerprint is not
            // registered in Firebase for this build. 7 is no network.
            Log.w(TAG, "Google sign-in failed: ${e.statusCode} ${CommonStatusCodes.getStatusCodeString(e.statusCode)}")
            viewModel.reportGoogleSignInFailure()
        }
    }

    OutlinedButton(
        onClick = {
            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(webClientId)
                .requestEmail()
                .build()
            launcher.launch(GoogleSignIn.getClient(context, options).signInIntent)
        },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(R.drawable.ic_google),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = Color.Unspecified
            )
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.auth_continue_google), style = MaterialTheme.typography.titleSmall)
        }
    }
}

private const val TAG = "GoogleSignIn"
private const val SIGN_IN_CANCELLED = 12501
