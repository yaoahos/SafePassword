package com.safeincloud.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance

private val DefaultBrand = Color(0xFF1677FF)
private val LightBackground = Color(0xFFF4F4F6)
private val DarkBackground = Color(0xFF0C0C0E)
private val DarkSurface = Color(0xFF18181B)

@Composable
fun SafeInCloudTheme(
    themeColor: Color = DefaultBrand,
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) darkScheme(themeColor) else lightScheme(themeColor),
        typography = Typography(),
        content = content
    )
}

private fun lightScheme(primary: Color): ColorScheme {
    val onPrimary = readableOn(primary)
    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primary.copy(alpha = 0.14f).compositeOver(Color.White),
        onPrimaryContainer = primary.darken(0.35f),
        secondary = Color(0xFF6B6B70),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF0F0F3),
        onSecondaryContainer = Color(0xFF111113),
        tertiary = primary,
        onTertiary = onPrimary,
        background = LightBackground,
        onBackground = Color(0xFF0A0A0B),
        surface = Color.White,
        onSurface = Color(0xFF0A0A0B),
        surfaceVariant = Color(0xFFF0F0F3),
        onSurfaceVariant = Color(0xFF8A8A8E),
        error = Color(0xFFFF3B30),
        outline = Color(0xFFECECEF)
    )
}

private fun darkScheme(primary: Color): ColorScheme {
    val onPrimary = readableOn(primary)
    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primary.copy(alpha = 0.24f).compositeOver(DarkSurface),
        onPrimaryContainer = Color(0xFFFFD8A8),
        secondary = Color(0xFFB8B8BE),
        onSecondary = Color(0xFF17171A),
        secondaryContainer = Color(0xFF242428),
        onSecondaryContainer = Color(0xFFE7E7EA),
        tertiary = primary,
        onTertiary = onPrimary,
        background = DarkBackground,
        onBackground = Color(0xFFF5F5F7),
        surface = DarkSurface,
        onSurface = Color(0xFFF5F5F7),
        surfaceVariant = Color(0xFF242428),
        onSurfaceVariant = Color(0xFF8E8E93),
        error = Color(0xFFFF6B63),
        outline = Color(0xFF2A2A2E)
    )
}

private fun readableOn(color: Color): Color =
    if (color.luminance() > 0.55f) Color(0xFF111113) else Color.White

private fun Color.darken(amount: Float): Color = Color(
    red = (red * (1f - amount)).coerceIn(0f, 1f),
    green = (green * (1f - amount)).coerceIn(0f, 1f),
    blue = (blue * (1f - amount)).coerceIn(0f, 1f),
    alpha = alpha
)
