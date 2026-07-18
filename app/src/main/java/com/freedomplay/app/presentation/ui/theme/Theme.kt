package com.freedomplay.app.presentation.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AmoledDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF4081),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFF4081).copy(alpha = 0.15f),
    onPrimaryContainer = Color(0xFFFF4081),
    secondary = Color(0xFF7C4DFF),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF7C4DFF).copy(alpha = 0.15f),
    tertiary = Color(0xFF00E5FF),
    background = Color(0xFF000000),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF1A1A2E),
    onSurfaceVariant = Color(0xFFB0B0C0),
    error = Color(0xFFF11A22),
    outline = Color(0xFF2A2A3E),
    outlineVariant = Color(0xFF3A3A4E)
)

val DeepGradientDarkColorScheme = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF7C4DFF).copy(alpha = 0.15f),
    secondary = Color(0xFFFF4081),
    tertiary = Color(0xFF00E5FF),
    background = Color(0xFF0D0D1A),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF0D0D1A),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF1A1A2E),
    onSurfaceVariant = Color(0xFFB0B0C0)
)

val Material3DarkColorScheme = darkColorScheme(
    primary = Color(0xFF90CAF9),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF00497D),
    secondary = Color(0xFF80CBC4),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0)
)

val LightColorScheme = lightColorScheme(
    primary = Color(0xFF7C4DFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DEFF),
    secondary = Color(0xFFFF4081),
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F)
)

enum class ThemeType { AMOLED, GRADIENT, MATERIAL3, LIGHT }

@Composable
fun FreedomPlayTheme(
    themeType: ThemeType = ThemeType.AMOLED,
    content: @Composable () -> Unit
) {
    val colorScheme = when (themeType) {
        ThemeType.AMOLED -> AmoledDarkColorScheme
        ThemeType.GRADIENT -> DeepGradientDarkColorScheme
        ThemeType.MATERIAL3 -> Material3DarkColorScheme
        ThemeType.LIGHT -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
