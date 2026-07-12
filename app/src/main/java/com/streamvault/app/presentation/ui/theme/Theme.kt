package com.streamvault.app.presentation.ui.theme

import android.app.Activity
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class Theme(val id: String, val name: String, val accent: Color)

val AppThemes = listOf(
    Theme("hot_pink", "Hot Pink", Color(0xFFFF4081)),
    Theme("digital_waves", "Digital Waves", Color(0xFF4FC3F7)),
    Theme("eco_frequency", "Eco Frequency", Color(0xFF69F0AE)),
    Theme("neon_purple", "Neon Purple", Color(0xFFB388FF)),
    Theme("amber_horizon", "Amber Horizon", Color(0xFFFFB74D)),
    Theme("crimson", "Crimson", Color(0xFFFF5252)),
)

fun Theme.toColorScheme(darkTheme: Boolean, amoledMode: Boolean): ColorScheme {
    val bg = when {
        !darkTheme -> Color(0xFFFAFAFA)
        amoledMode -> Color(0xFF000000)
        else -> Color(0xFF121212)
    }
    val surface = when {
        !darkTheme -> Color(0xFFFFFFFF)
        amoledMode -> Color(0xFF000000)
        else -> Color(0xFF1A1A1A)
    }
    val surfaceVariant = when {
        !darkTheme -> Color(0xFFF0F5F0)
        amoledMode -> Color(0xFF0A0A0A)
        else -> Color(0xFF1A1C1A)
    }
    val onBg = if (!darkTheme) Color(0xFF1A1A1A) else Color(0xFFE0E0E0)
    val onSurface = if (!darkTheme) Color(0xFF1A1A1A) else Color(0xFFE0E0E0)
    val onSurfaceVariant = if (!darkTheme) Color(0xFF4A5568) else Color(0xFFB0B0B0)
    val outline = if (!darkTheme) Color(0xFF79747E) else Color(0xFF1A201A)

    return if (!darkTheme) {
        lightColorScheme(
            primary = accent,
            onPrimary = Color(0xFFFFFFFF),
            secondary = accent,
            onSecondary = Color(0xFFFFFFFF),
            tertiary = accent,
            onTertiary = Color(0xFFFFFFFF),
            background = bg,
            onBackground = onBg,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            error = Color(0xFFDC2626),
            onError = Color(0xFFFFFFFF),
            outline = outline,
            outlineVariant = Color(0xFFCAC4D0)
        )
    } else {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF000000),
            secondary = accent,
            onSecondary = Color(0xFF000000),
            tertiary = accent,
            onTertiary = Color(0xFF000000),
            background = bg,
            onBackground = onBg,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            surfaceContainerLow = surface,
            surfaceContainer = surface,
            surfaceContainerHigh = surfaceVariant,
            error = Color(0xFFDC2626),
            onError = Color(0xFFFFFFFF),
            outline = outline,
            outlineVariant = outline
        )
    }
}

@Composable
fun FreedomPlayTheme(
    darkTheme: Boolean = true,
    amoledMode: Boolean = true,
    accent: Color = AppThemes.first().accent,
    content: @Composable () -> Unit
) {
    val colorScheme = Theme("", "", accent).toColorScheme(darkTheme, amoledMode)

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
