package dev.tlong.traveler.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// The Concierge itinerary palette: forest green on warm paper.
private val Light = lightColorScheme(
    primary = Color(0xFF2F5D50), onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE9E3), onPrimaryContainer = Color(0xFF12302A),
    secondary = Color(0xFF7D9C8F), onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EFEA), onSecondaryContainer = Color(0xFF1E3A33),
    tertiary = Color(0xFFB27628), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF4DF), onTertiaryContainer = Color(0xFF5E3B0A),
    background = Color(0xFFF5F3EE), onBackground = Color(0xFF222222),
    surface = Color(0xFFF5F3EE), onSurface = Color(0xFF222222),
    surfaceVariant = Color(0xFFEEEAE2), onSurfaceVariant = Color(0xFF555049),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBFAF7),
    surfaceContainer = Color(0xFFF1EEE7), surfaceContainerHigh = Color(0xFFEBE7DF), surfaceContainerHighest = Color(0xFFE4DFD5),
    outline = Color(0xFF8B857A), outlineVariant = Color(0xFFDDD7CC),
    error = Color(0xFFB3261E),
    inverseSurface = Color(0xFF2C3330), inverseOnSurface = Color(0xFFEFEDE8), inversePrimary = Color(0xFF9CCFBE),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF9CCFBE), onPrimary = Color(0xFF0B2E25),
    primaryContainer = Color(0xFF24473D), onPrimaryContainer = Color(0xFFDBE9E3),
    secondary = Color(0xFFA9C3B8), onSecondary = Color(0xFF14302A),
    secondaryContainer = Color(0xFF2B3D37), onSecondaryContainer = Color(0xFFDDE8E3),
    tertiary = Color(0xFFF0C27A), onTertiary = Color(0xFF3E2A05),
    tertiaryContainer = Color(0xFF4A3613), onTertiaryContainer = Color(0xFFFFE4B8),
    background = Color(0xFF121614), onBackground = Color(0xFFE4E2DD),
    surface = Color(0xFF121614), onSurface = Color(0xFFE4E2DD),
    surfaceVariant = Color(0xFF2A302D), onSurfaceVariant = Color(0xFFC2C8C3),
    surfaceContainerLowest = Color(0xFF0D100F), surfaceContainerLow = Color(0xFF181D1B),
    surfaceContainer = Color(0xFF1C2220), surfaceContainerHigh = Color(0xFF262C2A), surfaceContainerHighest = Color(0xFF313735),
    outline = Color(0xFF8C928E), outlineVariant = Color(0xFF3E4542),
    inversePrimary = Color(0xFF2F5D50),
)

/** Colours the map draws with; they follow the theme so the map works in dark mode too. */
@Immutable
data class MapColors(val water: Color, val land: Color, val border: Color, val route: Color, val marker: Color, val onMarker: Color, val label: Color)

val LocalMapColors = staticCompositionLocalOf { mapColors(Light, false) }

private fun mapColors(s: ColorScheme, dark: Boolean) = if (dark) {
    MapColors(Color(0xFF16232A), Color(0xFF263330), Color(0xFF45524E), s.tertiary, s.primary, s.onPrimary, s.onSurface)
} else {
    MapColors(Color(0xFFDCE8EE), Color(0xFFF7F4EC), Color(0xFFC9C1B2), s.tertiary, s.primary, s.onPrimary, s.onSurface)
}

@Composable
fun TravelerTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = if (dark) Dark else Light
    androidx.compose.runtime.CompositionLocalProvider(LocalMapColors provides mapColors(scheme, dark)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
