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
    primary = Color(0xFF22C55E),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF0A2E14),
    onPrimaryContainer = Color(0xFFB8F5C5),
    secondary = Color(0xFF22C55E),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF111211),
    onSecondaryContainer = Color(0xFFB8F5C5),
    tertiary = Color(0xFFE8813B),
    onTertiary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF111211),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF1A1C1A),
    onSurfaceVariant = Color(0xFFB0B0B0),
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF111211),
    surfaceContainerHigh = Color(0xFF1A1C1A),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFF1A201A),
    outlineVariant = Color(0xFF222822)
)

private val AmoledColorScheme = darkColorScheme(
    primary = Color(0xFF22C55E),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF0A2E14),
    onPrimaryContainer = Color(0xFFB8F5C5),
    secondary = Color(0xFF22C55E),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF000000),
    onSecondaryContainer = Color(0xFFB8F5C5),
    tertiary = Color(0xFFE8813B),
    onTertiary = Color(0xFF000000),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF0A0A0A),
    onSurfaceVariant = Color(0xFFB0B0B0),
    surfaceContainerLow = Color(0xFF000000),
    surfaceContainer = Color(0xFF000000),
    surfaceContainerHigh = Color(0xFF0A0A0A),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    outline = Color(0xFF1A201A),
    outlineVariant = Color(0xFF1A251A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF16A34A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCFCE7),
    onPrimaryContainer = Color(0xFF052E16),
    secondary = Color(0xFF16A34A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8F5E9),
    onSecondaryContainer = Color(0xFF052E16),
    tertiary = Color(0xFFE8813B),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFAFAFA),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF0F5F0),
    onSurfaceVariant = Color(0xFF4A5568),
    error = Color(0xFFDC2626),
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
        !darkTheme -> LightColorScheme
        amoledMode -> AmoledColorScheme
        else -> DarkColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Black.toArgb()
            val isLight = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = isLight
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = isLight
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
