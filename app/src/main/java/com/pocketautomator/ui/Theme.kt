package com.pocketautomator.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Background = Color(0xFF0D1117)
private val Surface = Color(0xFF161B22)
private val SurfaceVariant = Color(0xFF21262D)
private val Primary = Color(0xFF7C6BF5)
private val OnPrimary = Color(0xFFFFFFFF)
private val Secondary = Color(0xFF58D4C8)
private val OnBackground = Color(0xFFE6EDF3)
private val OnSurface = Color(0xFFE6EDF3)
private val OnSurfaceVariant = Color(0xFF8B949E)
private val Outline = Color(0xFF30363D)
private val Error = Color(0xFFFF6B6B)

private val PocketAutomatorColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    secondary = Secondary,
    onSecondary = Color(0xFF0D1117),
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    error = Error,
    onError = Color(0xFFFFFFFF)
)

@Composable
fun PocketAutomatorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PocketAutomatorColorScheme,
        content = content
    )
}
