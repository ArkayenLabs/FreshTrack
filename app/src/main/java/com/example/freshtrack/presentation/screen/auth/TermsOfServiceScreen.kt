package com.example.freshtrack.presentation.screen.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfServiceScreen(
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terms of Service") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = "FreshTrack Terms of Service",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = """
                    Welcome to FreshTrack! 
                    
                    By using our application, you agree to the following terms:
                    
                    1. Acceptance of Terms
                    By creating an account and using FreshTrack, you agree to be bound by these Terms of Service.
                    
                    2. User Accounts
                    You are responsible for safeguarding your password and any activities or actions under your account.
                    
                    3. Data Privacy
                    Your data is stored securely. We do not sell your personal data. Please refer to our Privacy Policy for more information.
                    
                    4. Limitation of Liability
                    FreshTrack is provided "as is" without warranties of any kind. We are not responsible for any issues arising from the use of this app.
                    
                    5. Changes to Terms
                    We reserve the right to modify these terms at any time. Continued use of the app constitutes acceptance of the new terms.
                    
                    (This is a placeholder Terms of Service and should be replaced by a formal legal document).
                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
