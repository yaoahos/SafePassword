package com.yaoahos.safepassword.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DefaultBrand = Color(0xFF1677FF)
private val LightBackground = Color(0xFFF4F4F6)
internal val DarkBackground = Color(0xFF0C0C0E)
private val DarkSurface = Color(0xFF18181B)

@Composable
fun SafePasswordTheme(
    themeColor: Color = DefaultBrand,
    /** 跟随桌面壁纸：主色取系统从壁纸提取的结果，忽略 [themeColor]。 */
    dynamic: Boolean = false,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // 门槛与壁纸监听共用 WallpaperSeed.kt 的 MIN_WALLPAPER_SDK，别再各写一份。
    // 低版本自动落回自选色，不报错也不把界面画成灰。
    val dynamicSupported = dynamic && wallpaperColorSupported()
    val refreshScope = rememberCoroutineScope()
    // 订阅壁纸色变化：前台换壁纸（分屏 / 悬浮窗）也能立刻更新，不用等 Activity resume。
    // key 里带 dynamicSupported，拨开关那一帧就重新取初值，不会拿着关闭时的 null。
    val wallpaperSeed by produceState<Color?>(null, context, dynamicSupported) {
        if (!dynamicSupported) {
            value = null
            return@produceState
        }
        // getWallpaperColors 走 IPC，AOSP 不建议在 UI 线程调用，首帧读取放 IO。
        value = withContext(Dispatchers.IO) { wallpaperSeedColor(context) }
        withContext(Dispatchers.IO) { WallpaperColorBus.refresh(context) }
        WallpaperColorBus.seed.collect { latest ->
            // 读不到时保留上一次的值：换壁纸瞬间系统可能还没算完色，
            // 那一下若切回系统整套配色，界面会闪一次紫底。
            if (latest != null) value = latest
        }
    }
    // 部分 ROM 不发 OnColorsChanged 回调，回前台再兜底重读一次。
    if (dynamicSupported) {
        LifecycleResumeEffect(Unit) {
            refreshScope.launch(Dispatchers.IO) { WallpaperColorBus.refresh(context) }
            onPauseOrDispose { }
        }
    }
    // 优先用系统对壁纸的取色结果自己算主色（只算 primary，surface 层继续用本文件里的
    // 中性色）；拿不到才交给系统整套方案。理由见 WallpaperSeed.kt 的注释：
    // 系统 accent overlay 在部分 ROM 上不跟壁纸更新，且整套方案会把弹窗/菜单的底色
    // 换回带紫调的基线值。
    val seed = if (dynamicSupported) wallpaperSeed else null
    val scheme = when {
        seed != null -> {
            val primary = seedToPrimary(seed, darkTheme)
            if (darkTheme) darkScheme(primary) else lightScheme(primary)
        }
        dynamicSupported ->
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
        // 原来写死橙 #FFD8A8。浅色模式这一层跟随 primary 压暗，深色却固定橙色，
        // 换到冷色主题（尤其壁纸取色拿到蓝/绿）就成了「蓝底橙字」。改成中性近白：
        // 任意 primary 叠出来的容器层都够对比，也不再跟主题色抢眼。
        onPrimaryContainer = Color(0xFFF2F2F5),
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
