package com.streamvault.app.presentation.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF00BCD4),        // Cyan - streaming/freedom
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF00838F),
    onPrimaryContainer = Color(0xFFB2EBF2),
    secondary = Color(0xFFB388FF),      // Soft purple - anime
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF7C4DFF),
    onSecondaryContainer = Color(0xFFE8DEFF),
    tertiary = Color(0xFFFF4081),       // Bright pink - Japanese anime
    onTertiary = Color(0xFF000000),
    background = Color(0xFF0A0A1A),     // Deep navy
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF12122A),        // Slightly lighter navy
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF1A1A3A),
    onSurfaceVariant = Color(0xFFB0B0C0),
    surfaceContainerLow = Color(0xFF0E0E22),
    surfaceContainer = Color(0xFF14142E),
    surfaceContainerHigh = Color(0xFF1A1A3A),
    error = Color(0xFFEF5350),
    onError = Color(0xFF000000),
    outline = Color(0xFF2A2A4A),
    outlineVariant = Color(0xFF3A3A5A)
)

private val AmoledColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),        // Brighter cyan on AMOLED
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF00838F),
    onPrimaryContainer = Color(0xFFB2EBF2),
    secondary = Color(0xFFB388FF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF7C4DFF),
    onSecondaryContainer = Color(0xFFE8DEFF),
    tertiary = Color(0xFFFF4081),
    onTertiary = Color(0xFF000000),
    background = Color(0xFF000000),     // True black
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF0D0D1A),
    onSurfaceVariant = Color(0xFFB0B0C0),
    surfaceContainerLow = Color(0xFF080812),
    surfaceContainer = Color(0xFF0F0F1A),
    surfaceContainerHigh = Color(0xFF141425),
    error = Color(0xFFEF5350),
    onError = Color(0xFF000000),
    outline = Color(0xFF1E1E3E),
    outlineVariant = Color(0xFF2A2A4A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0097A7),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB2EBF2),
    onPrimaryContainer = Color(0xFF00363A),
    secondary = Color(0xFF7C4DFF),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEFF),
    onSecondaryContainer = Color(0xFF1A0040),
    tertiary = Color(0xFFD81B60),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF5F5FF),
    onBackground = Color(0xFF1A1A2E),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A2E),
    surfaceVariant = Color(0xFFE8E0F0),
    onSurfaceVariant = Color(0xFF49454F),
    error = Color(0xFFE53935),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0)
)

@Composable
fun FreedomPlayTheme(
    darkTheme: Boolean = true,
    amoledMode: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        amoledMode -> AmoledColorScheme
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Black.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
