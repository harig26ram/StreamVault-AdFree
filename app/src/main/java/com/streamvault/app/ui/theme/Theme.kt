package com.streamvault.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Secondary,
    onPrimary = TextPrimary,
    primaryContainer = Primary,
    onPrimaryContainer = TextPrimary,
    secondary = Accent,
    onSecondary = Color.Black,
    secondaryContainer = DarkCard,
    onSecondaryContainer = TextPrimary,
    tertiary = Info,
    onTertiary = TextPrimary,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = TextSecondary,
    outline = DarkBorder,
    error = Error,
    onError = TextPrimary
)

private val LightColorScheme = lightColorScheme(
    primary = Secondary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = Primary,
    secondary = Accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCFCE7),
    onSecondaryContainer = Color(0xFF14532D),
    tertiary = Info,
    onTertiary = Color.White,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF475569),
    outline = Color(0xFFE2E8F0),
    error = Error,
    onError = Color.White
)

@Composable
fun StreamVaultTheme(
    darkTheme: Boolean = true, // Default to dark for streaming app
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor -> {
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
