package com.example.freshtrack.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Dark Color Scheme - Following 60:30:10 Rule
 * FreshTrack uses dark theme only for a modern, eye-friendly experience
 */
private val DarkColorScheme = darkColorScheme(
    // PRIMARY (60%) - Light Green (inverted for dark)
    primary = Color(0xFF81C784),              // Light green
    onPrimary = Color(0xFF1B5E20),            // Dark green
    primaryContainer = Color(0xFF2E7D32),     // Medium green background
    onPrimaryContainer = Color(0xFFC8E6C9),   // Very light green text

    // SECONDARY (30%) - Light Blue
    secondary = Color(0xFF64B5F6),            // Light blue
    onSecondary = Color(0xFF0D47A1),          // Dark blue
    secondaryContainer = Color(0xFF1976D2),   // Medium blue background
    onSecondaryContainer = Color(0xFFBBDEFB), // Very light blue text

    // TERTIARY (10%) - Light Orange
    tertiary = Color(0xFFFFB74D),             // Light orange
    onTertiary = Color(0xFFE65100),           // Dark orange
    tertiaryContainer = Color(0xFFF57C00),    // Medium orange background
    onTertiaryContainer = Color(0xFFFFE0B2),  // Very light orange text

    // ERROR - Light Red
    error = Color(0xFFEF5350),
    onError = Color(0xFFB71C1C),
    errorContainer = Color(0xFFC62828),
    onErrorContainer = Color(0xFFFFCDD2),

    // SURFACE & BACKGROUND - Dark grays
    background = Color(0xFF121212),           // Very dark gray
    onBackground = Color(0xFFE6E1E5),         // Light gray text
    surface = Color(0xFF1E1E1E),              // Dark gray cards
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2C2C2C),       // Medium dark gray
    onSurfaceVariant = Color(0xFFCAC4D0),     // Light gray text

    // OUTLINE
    outline = Color(0xFF424242),
    outlineVariant = Color(0xFF303030),

    // INVERSE
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = Color(0xFF4CAF50)
)

@Composable
fun FreshTrackTheme(
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabled to use our custom colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            dynamicDarkColorScheme(context)
        }
        else -> DarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(), // We'll use default Material 3 shapes
        content = content
    )
}