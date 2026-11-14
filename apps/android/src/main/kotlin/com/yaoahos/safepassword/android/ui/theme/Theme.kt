package com.yaoahos.safepassword.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

private val DefaultBrand = Color(0xFF1677FF)
private val LightBackground = Color(0xFFF4F4F6)
private val DarkBackground = Color(0xFF0C0C0E)
private val DarkSurface = Color(0xFF18181B)

@Composable
fun SafePasswordTheme(
    themeColor: Color = DefaultBrand,
    /** 跟随桌面壁纸：用系统按壁纸生成的整套配色，忽略 [themeColor]。 */
    dynamic: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // 系统取色是 Android 12 起才有的能力；低版本自动落回自选色，不报错也不把界面画成灰，
    // 主题页会直接写明「需要 Android 12 及以上」，不让用户对着一个点不动的开关猜。
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkScheme(themeColor)
        else -> lightScheme(themeColor)
    }
    MaterialTheme(
        colorScheme = scheme,
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
