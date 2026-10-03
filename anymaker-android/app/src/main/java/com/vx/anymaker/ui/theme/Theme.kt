package com.vx.anymaker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

internal val LightScheme: ColorScheme = lightColorScheme(
    primary = BrandTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDFF1EE),
    onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF2563A8),
    onSecondary = Color.White,
    tertiary = StampLight,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = Color(0xFFB3261E),
)

internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = BrandTealDark,
    onPrimary = Color(0xFF00201E),
    primaryContainer = Color(0xFF13302E),
    onPrimaryContainer = Color(0xFFBFEDE8),
    secondary = Color(0xFF7FB0EA),
    onSecondary = Color(0xFF0B1D30),
    tertiary = StampDark,
    onTertiary = Color(0xFF2A1308),
    background = DarkBackground,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurface,
    surfaceContainerHigh = DarkSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = Color(0xFFF2B8B5),
)

private val LocalToolColors = staticCompositionLocalOf { LightToolColors }

/** Section colors (photo, scan, PDF) that sit beside the Material color scheme. */
object AnymakerColors {
    val tools: ToolColors
        @Composable @ReadOnlyComposable get() = LocalToolColors.current
}

@Composable
fun AnymakerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            // Keep the brand's calm neutrals; take only the accent hues from the wallpaper.
            val brand = if (darkTheme) DarkScheme else LightScheme
            brand.copy(
                primary = dynamic.primary,
                onPrimary = dynamic.onPrimary,
                primaryContainer = dynamic.primaryContainer,
                onPrimaryContainer = dynamic.onPrimaryContainer,
            )
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(LocalToolColors provides if (darkTheme) DarkToolColors else LightToolColors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AnymakerTypography,
            shapes = AnymakerShapes,
            content = content,
        )
    }
}
