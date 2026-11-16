package com.yaoahos.safepassword.android.ui.theme

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

private const val TAG = "SPV-THEME"

/**
 * 动态取色开关、壁纸色读取、壁纸色监听共用的最低系统版本：Android 12（S / 31）。
 *
 * 以前这里按 34 分段，理由是「getPrimaryColor() 在 31–33 返回 int」——核实后
 * 该前提不成立：AOSP android-13.0.0_r1 是 `public @NonNull Color getPrimaryColor()`，
 * 官方文档标注 Added in API level 27。getWallpaperColors(FLAG_SYSTEM) 与
 * addOnColorsChangedListener 同为 27+，Color.toArgb() 是 26+。
 *
 * 门槛只在这里定义一次，Theme / 外观页 / Application 都通过 [wallpaperColorSupported]
 * 判断，避免再出现「开关 31+ 可用、取色却要 34+」这类双重口径。
 */
internal const val MIN_WALLPAPER_SDK = 31

internal fun wallpaperColorSupported(): Boolean = Build.VERSION.SDK_INT >= MIN_WALLPAPER_SDK

/** 只响应桌面壁纸（FLAG_SYSTEM）变化；单独换锁屏壁纸不该改主色。 */
internal fun isSystemWallpaperChange(which: Int): Boolean =
    which and WallpaperManager.FLAG_SYSTEM != 0

/**
 * 壁纸色变化的应用内广播。Theme 订阅它，Application 在系统回调时 publish。
 *
 * 之前只在 Activity 回到前台时重读壁纸色：用户去系统设置换完壁纸直接回桌面，
 * App 根本没 resume，主色还是旧的。这里由 Application 注册
 * WallpaperManager.OnColorsChangedListener，回调一到就重算。
 */
internal object WallpaperColorBus {
    private val _seed = MutableStateFlow<Color?>(null)
    val seed: StateFlow<Color?> = _seed

    /** 优先用回调带来的颜色：部分 ROM 触发回调时 getWallpaperColors 缓存还没刷新。 */
    // primaryColor 是 API 27，lint 按 minSdk 报警；这个类只被
    // wallpaperColorSupported()（31）之后的 OnColorsChangedListener 调用。
    @Suppress("NewApi")
    fun publish(context: Context, colors: WallpaperColors?) {
        val argb = colors?.let { runCatching { it.primaryColor.toArgb() }.getOrNull() }
        val next = if (argb != null && isUsableSeed(argb)) Color(argb) else wallpaperSeedColor(context)
        _seed.value = next
        Log.d(TAG, "壁纸色变化 #%06X".format((next?.toArgb() ?: 0) and 0xFFFFFF))
    }

    fun refresh(context: Context) {
        _seed.value = wallpaperSeedColor(context)
    }
}

/**
 * 取系统对当前壁纸的取色结果，作为动态配色的种子。
 *
 * 为什么不用 `dynamicLightColorScheme()` 直接当主色：它读的是 SystemUI 的
 * `com.android.systemui:accent` overlay。部分 ROM（HyperOS 3 实测）在
 * `dumpsys wallpaper` 里 `isColorExtracted=false` 时不会重算这个 overlay，
 * 于是一直停在旧值——壁纸已经换成蓝色，App 主色还是上一次的绿色。
 *
 * `WallpaperManager.getWallpaperColors()` 拿到的是系统对壁纸的真实提取结果，
 * 不经过 overlay，因此绕开了这个卡住的环节。读不到时返回 null，由调用方回落。
 */
internal fun wallpaperSeedColor(context: Context): Color? {
    if (!wallpaperColorSupported()) return null
    return try {
        val colors = WallpaperManager.getInstance(context)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        if (colors == null) {
            Log.d(TAG, "壁纸色缓存为空，回落系统方案")
            return null
        }
        // 只取 primaryColor 会踩坑：部分 ROM（实测 HyperOS / Android 17 组合）
        // 三个候选里只有 secondary / tertiary 是真实色，primary 直接给 #000000，
        // 黑色进 HSL 后饱和度恒为 0，整屏就被推成灰的。
        // 这里把三个候选都拿出来，按可用性过滤后取最鲜艳的一个。
        val candidates = listOf(
            "primary" to colors.primaryColor,
            "secondary" to colors.secondaryColor,
            "tertiary" to colors.tertiaryColor
        ).mapNotNull { (tag, color) ->
            runCatching { color?.toArgb() }.getOrNull()?.let { tag to it }
        }
        val usable = candidates.filter { (_, argb) -> isUsableSeed(argb) }
        if (usable.isEmpty()) {
            Log.d(
                TAG,
                "壁纸候选色都不可用（${
                    candidates.joinToString { "%s=#%06X".format(it.first, it.second and 0xFFFFFF) }
                }），回落系统方案"
            )
            return null
        }
        val best = usable.maxByOrNull { (_, argb) -> seedScore(argb) }!!
        Log.d(TAG, "壁纸种子色 #%06X（来自 %s）".format(best.second and 0xFFFFFF, best.first))
        Color(best.second)
    } catch (t: Throwable) {
        Log.w(TAG, "读取壁纸色失败：${t.javaClass.simpleName}: ${t.message}")
        null
    }
}

/** 纯黑、透明、近灰都不适合当种子：喂进 HSL 后饱和度接近 0，界面会整片发灰。 */
internal fun isUsableSeed(argb: Int): Boolean {
    val alpha = argb ushr 24 and 0xFF
    if (alpha < 0x10) return false
    val r = argb shr 16 and 0xFF
    val g = argb shr 8 and 0xFF
    val b = argb and 0xFF
    if (maxOf(r, g, b) < 24) return false
    val (_, saturation, _) = hslOf(argb)
    return saturation >= MIN_SEED_SATURATION
}

/** 候选排序：饱和度优先，亮度只做兜底参考，避免挑到几乎全黑的深色。 */
internal fun seedScore(argb: Int): Float {
    val (_, saturation, lightness) = hslOf(argb)
    return saturation * 0.8f + (lightness * 0.4f).coerceAtMost(0.25f)
}

/** 低于这个饱和度视为「近灰」，不能作为动态配色的种子。 */
private const val MIN_SEED_SATURATION = 0.12f

/** 纯 Kotlin HSL，避免 android.graphics.Color 在 JVM 单测里是返回 0 的桩。 */
private fun hslOf(argb: Int): Triple<Float, Float, Float> {
    val r = (argb shr 16 and 0xFF) / 255f
    val g = (argb shr 8 and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val lightness = (max + min) / 2f
    if (delta == 0f) return Triple(0f, 0f, lightness)
    val saturation = delta / (1f - abs(2f * lightness - 1f))
    val raw = when (max) {
        r -> ((g - b) / delta) % 6f
        g -> (b - r) / delta + 2f
        else -> (r - g) / delta + 4f
    }
    var hue = raw * 60f
    if (hue < 0f) hue += 360f
    return Triple(hue, saturation, lightness)
}

/**
 * 把种子色规整成能当 primary 用的色调：色相不动，饱和度略收，明度压到与白字
 * （浅色）或深底（深色）保持 4.5:1 对比的位置。
 *
 * 只做「主色」这一层的换算，是为了让 surface / 容器层级继续沿用 [lightScheme] /
 * [darkScheme] 里既有的中性色——那里有明确约定：弹窗和菜单不能跟着主题色染色。
 */
internal fun seedToPrimary(seed: Color, dark: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    val hue = hsl[0]
    val sourceSaturation = hsl[1]
    val saturation = if (sourceSaturation < 0.10f) {
        sourceSaturation
    } else {
        (sourceSaturation * 0.92f).coerceIn(0.22f, 0.68f)
    }
    var lightness = if (dark) 0.70f else 0.42f
    val step = if (dark) 0.025f else -0.025f
    var candidate = Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness)))
    repeat(24) {
        val enoughContrast = if (dark) {
            ColorUtils.calculateContrast(DarkBackground.toArgb(), candidate.toArgb()) >= 4.5
        } else {
            ColorUtils.calculateContrast(Color.White.toArgb(), candidate.toArgb()) >= 4.5
        }
        if (enoughContrast) return candidate
        lightness = (lightness + step).coerceIn(0.06f, 0.96f)
        candidate = Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness)))
    }
    return candidate
}
