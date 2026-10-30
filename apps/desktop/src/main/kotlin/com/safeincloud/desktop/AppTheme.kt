package com.safeincloud.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TealPrimary = Color(0xFF0F766E)
private val TealSecondary = Color(0xFF14B8A6)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = TealPrimary,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFCFEDE9),
            onPrimaryContainer = Color(0xFF00201C),
            secondary = TealSecondary,
            onSecondary = Color.White,
            background = Color(0xFFF8FAFB),
            onBackground = Color(0xFF191C1C),
            surface = Color(0xFFFCFDFB),
            onSurface = Color(0xFF191C1C),
            surfaceVariant = Color(0xFFE0E3E1),
            onSurfaceVariant = Color(0xFF404947),
            error = Color(0xFFBA1A1A),
            onError = Color.White
        ),
        content = content
    )
}
