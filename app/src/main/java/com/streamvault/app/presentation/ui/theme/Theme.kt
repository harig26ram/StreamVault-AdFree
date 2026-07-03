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
    primary = Color(0xFF3B82F6),        // Electric Blue
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E40AF),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF64748B),       // Slate Gray
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF334155),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFF8B5CF6),        // Purple accent
    onTertiary = Color.White,
    background = Color(0xFF000000),      // OLED Black
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0F172A),         // Dark surface
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1E293B),  // Card background
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFEF4444),
    onError = Color.White,
    outline = Color(0xFF334155)
)

private val AmoledColorScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E40AF),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF64748B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF111111),
    onSecondaryContainer = Color(0xFFE2E8F0),
    tertiary = Color(0xFF8B5CF6),
    onTertiary = Color.White,
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF0A0A0A),
    onSurfaceVariant = Color(0xFF94A3B8),
    error = Color(0xFFEF4444),
    onError = Color.White,
    outline = Color(0xFF1A1A1A)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E40AF),
    secondary = Color(0xFF475569),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF334155),
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    error = Color(0xFFDC2626),
    onError = Color.White,
    outline = Color(0xFFCBD5E1)
)

@Composable
fun StreamVaultTheme(
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