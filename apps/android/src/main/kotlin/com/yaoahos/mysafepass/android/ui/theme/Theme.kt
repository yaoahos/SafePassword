package com.yaoahos.mysafepass.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
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

/**
 * 动态取色此刻的实际结果，供主题页决定「预置配色该不该显示为选中」。
 *
 * 关键是 [FALLBACK]：开关是打开的，但壁纸色取不到（ROM 只给黑色 / 动态壁纸没实现
 * 取色 / IPC 异常），界面实际用的是自选配色。这时如果预置色块全都不选中，用户会
 * 以为「配色丢了」——把正在生效的那一块标出来才对得上眼睛看到的东西。
 */
enum class DynamicAccentState {
    /** 首帧还没读完壁纸色，先不做任何标记，避免闪一下再跳。 */
    PENDING,

    /** 真的在跟随壁纸，预置色块全部不选中。 */
    FOLLOWING,

    /** 取不到壁纸色，已回落自选配色；预置色块按 accent 选中。 */
    FALLBACK
}

val LocalDynamicAccentState = staticCompositionLocalOf { DynamicAccentState.FALLBACK }

/** 壁纸色的三种状态：读到了 / 读不到 / 还没读完。 */
private sealed interface SeedState {
    data object Pending : SeedState
    data object Unusable : SeedState
    data class Ready(val color: Color) : SeedState
}

private fun seedStateOf(seed: Color?): SeedState =
    if (seed == null) SeedState.Unusable else SeedState.Ready(seed)

@Composable
fun MySafePassTheme(
    themeColor: Color = DefaultBrand,
    /** 跟随桌面壁纸：主色取系统从壁纸提取的结果；取不到时回落到 [themeColor]。 */
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
    // 三态而非单纯 Color?：要区分「还没读完」和「确实读不到」，主题页才能给出正确提示。
    @Suppress("ProduceStateDoesNotAssignValue")
    val seedState by produceState<SeedState>(SeedState.Pending, context, dynamicSupported) {
        if (!dynamicSupported) {
            value = SeedState.Unusable
            return@produceState
        }
        // getWallpaperColors 走 IPC，AOSP 不建议在 UI 线程调用，首帧读取放 IO。
        value = seedStateOf(withContext(Dispatchers.IO) { wallpaperSeedColor(context) })
        withContext(Dispatchers.IO) { WallpaperColorBus.refresh(context) }
        WallpaperColorBus.seed.collect { latest ->
            // 回调带来的颜色不可用时再直读一次：部分 ROM 的 OnColorsChangedListener
            // 触发瞬间 getWallpaperColors 缓存还没刷新，直接判 Unusable 会误回落。
            // 直读也拿不到才算真的取不到色。
            value = seedStateOf(
                latest ?: withContext(Dispatchers.IO) { wallpaperSeedColor(context) }
            )
        }
    }
    // 部分 ROM 不发 OnColorsChanged 回调，回前台再兜底重读一次。
    if (dynamicSupported) {
        LifecycleResumeEffect(Unit) {
            refreshScope.launch(Dispatchers.IO) { WallpaperColorBus.refresh(context) }
            onPauseOrDispose { }
        }
    }
    // 只拿到可用的壁纸色才跟随壁纸；读不到时（部分 ROM 的 WallpaperColors 只给黑色，
    // 系统 overlay 也跟着派生出一片灰蓝）老老实实用自选配色。
    // 之前这里回落到 dynamicLight/DarkColorScheme，结果整屏是「来路不明的灰」——
    // 深蓝壁纸配灰界面，比不跟随更糟，已移除这条退路。
    val effectiveSeed = (seedState as? SeedState.Ready)?.color
    val scheme = when {
        effectiveSeed != null -> {
            val primary = seedToPrimary(effectiveSeed, darkTheme)
            if (darkTheme) darkScheme(primary) else lightScheme(primary)
        }
        darkTheme -> darkScheme(themeColor)
        else -> lightScheme(themeColor)
    }
    // 主题页据此标记预置配色：跟随壁纸时不选，回落自选色时选中正在用的那一块。
    val dynamicAccentState = when {
        seedState is SeedState.Ready -> DynamicAccentState.FOLLOWING
        seedState is SeedState.Pending -> DynamicAccentState.PENDING
        else -> DynamicAccentState.FALLBACK
    }
    CompositionLocalProvider(LocalDynamicAccentState provides dynamicAccentState) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(),
            content = content
        )
    }
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
