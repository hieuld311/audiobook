package com.ivi.audiobook.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val AudioBookColorScheme = darkColorScheme(
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    primary = AccentGreen,
    secondary = AccentGold,
    onBackground = OnSurfacePrimary,
    onSurface = OnSurfacePrimary,
    onSurfaceVariant = OnSurfaceSecondary,
    outline = OutlineDark,
)

@Composable
fun AudioBookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = AudioBookColorScheme,
        typography = AudioBookTypography,
        content = content,
    )
}
