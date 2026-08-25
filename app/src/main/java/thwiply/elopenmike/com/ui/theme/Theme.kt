package thwiply.elopenmike.com.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

private val DarkColorScheme = darkColorScheme(
    primary = ElectricBluePrimaryDark,
    onPrimary = ElectricBlueOnPrimaryDark,
    primaryContainer = ElectricBluePrimaryContainerDark,
    onPrimaryContainer = ElectricBlueOnPrimaryContainerDark,
    secondary = ElectricBlueSecondaryDark,
    onSecondary = ElectricBlueOnSecondaryDark,
    secondaryContainer = ElectricBlueSecondaryContainerDark,
    onSecondaryContainer = ElectricBlueOnSecondaryContainerDark,
    tertiary = ElectricBlueTertiaryDark,
    onTertiary = ElectricBlueOnTertiaryDark,
    tertiaryContainer = ElectricBlueTertiaryContainerDark,
    onTertiaryContainer = ElectricBlueOnTertiaryContainerDark,
    background = ElectricBlueBackgroundDark,
    onBackground = ElectricBlueOnBackgroundDark,
    surface = ElectricBlueSurfaceDark,
    onSurface = ElectricBlueOnSurfaceDark,
    surfaceVariant = ElectricBlueSurfaceVariantDark,
    onSurfaceVariant = ElectricBlueOnSurfaceVariantDark,
    outline = ElectricBlueOutlineDark
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricBluePrimaryLight,
    onPrimary = ElectricBlueOnPrimaryLight,
    primaryContainer = ElectricBluePrimaryContainerLight,
    onPrimaryContainer = ElectricBlueOnPrimaryContainerLight,
    secondary = ElectricBlueSecondaryLight,
    onSecondary = ElectricBlueOnSecondaryLight,
    secondaryContainer = ElectricBlueSecondaryContainerLight,
    onSecondaryContainer = ElectricBlueOnSecondaryContainerLight,
    tertiary = ElectricBlueTertiaryLight,
    onTertiary = ElectricBlueOnTertiaryLight,
    tertiaryContainer = ElectricBlueTertiaryContainerLight,
    onTertiaryContainer = ElectricBlueOnTertiaryContainerLight,
    background = ElectricBlueBackgroundLight,
    onBackground = ElectricBlueOnBackgroundLight,
    surface = ElectricBlueSurfaceLight,
    onSurface = ElectricBlueOnSurfaceLight,
    surfaceVariant = ElectricBlueSurfaceVariantLight,
    onSurfaceVariant = ElectricBlueOnSurfaceVariantLight,
    outline = ElectricBlueOutlineLight
)

@Composable
fun ThwiplyTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}