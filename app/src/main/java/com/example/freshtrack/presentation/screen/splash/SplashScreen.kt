package com.example.freshtrack.presentation.screen.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.freshtrack.R
import kotlinx.coroutines.delay
import androidx.compose.ui.res.stringResource

@Composable
fun SplashScreen(
    onSplashComplete: () -> Unit
) {
    // Auto-navigate after a delay
    LaunchedEffect(Unit) {
        delay(2000)
        onSplashComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Use the standard theme background. It will be light or dark automatically.
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Display the logo from the drawable folder.
            Image(
                painter = painterResource(id = R.drawable.applogo),
                contentDescription = stringResource(R.string.app_logo_description),
                modifier = Modifier.size(128.dp) // Made icon slightly larger
            )

            Spacer(Modifier.height(24.dp))

            // App Name
            Text(
                text = stringResource(R.string.app_name),
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                // Use a color that works well on any background
                color = MaterialTheme.colorScheme.onBackground,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(8.dp))

            // Tagline
            Text(
                text = stringResource(R.string.app_tagline),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                // Use a secondary text color for the tagline.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )

            Spacer(Modifier.height(80.dp))

            // Loading indicator with the app's primary color.
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 3.dp
            )
        }
    }
}
