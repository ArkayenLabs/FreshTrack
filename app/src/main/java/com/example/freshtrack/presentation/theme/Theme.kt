package com.example.freshtrack.presentation.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Light Color Scheme - Following 60:30:10 Rule
 * 60% Primary (Green) - Main UI elements, headers, buttons
 * 30% Secondary (Blue) - Secondary actions, accents
 * 10% Accent (Orange) - Critical alerts, important actions
 */
private val LightColorScheme = lightColorScheme(
    // PRIMARY (60%) - Fresh Green
    primary = Color(0xFF4CAF50),              // Main green
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8F5E9),     // Very light green background
    onPrimaryContainer = Color(0xFF1B5E20),   // Dark green text

    // SECONDARY (30%) - Clean Blue
    secondary = Color(0xFF2196F3),            // Main blue
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3F2FD),   // Very light blue background
    onSecondaryContainer = Color(0xFF0D47A1), // Dark blue text

    // TERTIARY (10%) - Alert Orange (for warnings)
    tertiary = Color(0xFFFF9800),             // Warning orange
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE0B2),    // Light orange background
    onTertiaryContainer = Color(0xFFE65100),  // Dark orange text

    // ERROR - Critical Red
    error = Color(0xFFF44336),
    onError = Color.White,
    errorContainer = Color(0xFFFFEBEE),
    onErrorContainer = Color(0xFFB71C1C),

    // SURFACE & BACKGROUND - Clean whites
    background = Color(0xFFFAFAFA),           // Very light gray
    onBackground = Color(0xFF1C1B1F),         // Almost black text
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF5F5F5),       // Light gray cards
    onSurfaceVariant = Color(0xFF49454F),     // Medium gray text

    // OUTLINE - Borders and dividers
    outline = Color(0xFFE0E0E0),
    outlineVariant = Color(0xFFF5F5F5),

    // INVERSE (for dark elements on light theme)
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFF81C784)
)

/**
 * Dark Color Scheme - Following 60:30:10 Rule
 * Adjusted for dark mode with proper contrast
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
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false, // Disabled to use our custom colors
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes(), // We'll use default Material 3 shapes
        content = content
    )
}