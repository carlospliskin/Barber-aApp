package com.barberia.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val BarberColorScheme = darkColorScheme(
    primary = BarberColors.Gold,
    onPrimary = BarberColors.Obsidian,
    primaryContainer = BarberColors.GoldDeep,
    onPrimaryContainer = BarberColors.White,
    secondary = BarberColors.GoldLight,
    onSecondary = BarberColors.Obsidian,
    background = BarberColors.Obsidian,
    onBackground = BarberColors.Ivory,
    surface = BarberColors.Carbon,
    onSurface = BarberColors.Ivory,
    surfaceVariant = BarberColors.Graphite,
    onSurfaceVariant = BarberColors.Silver,
    outline = BarberColors.Mist,
    outlineVariant = BarberColors.Smoke,
    error = BarberColors.Error,
    onError = BarberColors.White,
)

/** Tema único de la app (siempre oscuro, como el diseño original). */
@Composable
fun BarberTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = BarberColorScheme, content = content)
}
