package com.example.freshtrack.presentation.screen.auth

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.example.freshtrack.R
import com.example.freshtrack.presentation.viewmodel.AuthViewModel
import org.koin.androidx.compose.koinViewModel

private const val PRIVACY_POLICY_URL = "https://www.arkayenlabs.com/privacy/freshtrack"

/**
 * Create an account. Same shape as sign-in so the two read as one flow:
 * Google first, email below, the agreement line as one sentence with two
 * links rather than three widgets fighting over spacing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    viewModel: AuthViewModel = koinViewModel(),
    onNavigateToLogin: () -> Unit,
    onNavigateToTerms: () -> Unit,
    onRegisterSuccess: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var agreeToTerms by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val passwordMismatch = confirmPassword.isNotEmpty() && password != confirmPassword

    LaunchedEffect(uiState.isSuccess) {
        if (uiState.isSuccess) {
            onRegisterSuccess()
            viewModel.resetState()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateToLogin) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.auth_create_account), style = MaterialTheme.typography.headlineMedium)
            Text(
                stringResource(R.string.auth_sign_in_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))

            GoogleSignInButton(viewModel = viewModel, enabled = !uiState.isLoading)

            Row(verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f))
                Text(
                    stringResource(R.string.auth_or),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider(Modifier.weight(1f))
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.auth_email)) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                singleLine = true
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(stringResource(R.string.auth_password)) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, null)
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                singleLine = true
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = { Text(stringResource(R.string.auth_confirm_password)) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                isError = passwordMismatch,
                supportingText = if (passwordMismatch) {
                    { Text(stringResource(R.string.auth_passwords_mismatch), color = MaterialTheme.colorScheme.error) }
                } else null,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                singleLine = true
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = agreeToTerms, onCheckedChange = { agreeToTerms = it })
                Text(
                    agreementText(
                        onTerms = onNavigateToTerms,
                        onPrivacy = { context.startActivity(Intent(Intent.ACTION_VIEW, PRIVACY_POLICY_URL.toUri())) }
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = uiState.error != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    text = uiState.error?.let { stringResource(messageFor(it)) } ?: "",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Button(
                onClick = { viewModel.registerWithEmail(email, password) },
                enabled = !uiState.isLoading && email.isNotBlank() && password.isNotBlank() && !passwordMismatch && agreeToTerms,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.5.dp)
                } else {
                    Text(stringResource(R.string.auth_create_account), style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.auth_have_account), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(6.dp))
                TextButton(onClick = onNavigateToLogin, shape = MaterialTheme.shapes.small) {
                    Text(stringResource(R.string.settings_sign_in), style = MaterialTheme.typography.titleSmall)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * "I agree to the Terms of Service and the Privacy Policy." as one sentence
 * from one resource, with the two names as links. The spacing is the
 * translator's, inside the string, instead of three composables each hoping
 * the next one starts with a gap.
 */
@Composable
private fun agreementText(onTerms: () -> Unit, onPrivacy: () -> Unit) = run {
    val terms = stringResource(R.string.auth_terms_link)
    val privacy = stringResource(R.string.auth_privacy_link)
    val sentence = stringResource(R.string.auth_agree_terms, terms, privacy)
    val linkStyle = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.primary))
    buildAnnotatedString {
        var cursor = 0
        for ((name, action) in listOf(terms to onTerms, privacy to onPrivacy)) {
            val at = sentence.indexOf(name, cursor)
            if (at < 0) continue
            append(sentence, cursor, at)
            withLink(LinkAnnotation.Clickable(tag = name, styles = linkStyle) { action() }) { append(name) }
            cursor = at + name.length
        }
        append(sentence, cursor, sentence.length)
    }
}
