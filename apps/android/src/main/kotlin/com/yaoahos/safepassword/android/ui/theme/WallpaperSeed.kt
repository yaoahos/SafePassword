package com.yaoahos.safepassword.android.ui.theme

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

private const val TAG = "SPV-THEME"

/**
 * 取系统对当前壁纸的取色结果，作为动态配色的种子。
 *
 * 为什么不用 `dynamicLightColorScheme()` 直接当主色：它读的是 SystemUI 的
 * `com.android.systemui:accent` overlay。部分 ROM（HyperOS 3 实测）在
 * `dumpsys wallpaper` 里 `isColorExtracted=false` 时不会重算这个 overlay，
 * 于是一直停在旧值——壁纸已经换成蓝色，App 主色还是上一次的绿色，
 * 用户看到的就是「动态取色没跟着壁纸变」。
 *
 * `WallpaperManager.getWallpaperColors()` 拿到的是系统对壁纸的真实提取结果，
 * 不经过 overlay，因此绕开了这个卡住的环节。读不到时返回 null，由调用方回落
 * 到系统方案（老系统 / ROM 未缓存 / 权限异常）。
 */
internal fun wallpaperSeedColor(context: Context): Color? {
    // 34 起 getPrimaryColor() 返回 android.graphics.Color；更早版本返回 int，
    // 跨版本签名不一致，直接按新签名调用会 ClassCastException，所以只在新版本启用，
    // 老版本继续走系统 overlay 方案。
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
    return try {
        val colors = WallpaperManager.getInstance(context)
            .getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        if (colors == null) {
            Log.d(TAG, "壁纸色缓存为空，回落系统方案")
            null
        } else {
            val argb = colors.primaryColor.toArgb()
            Log.d(TAG, "壁纸种子色 #%06X".format(argb and 0xFFFFFF))
            Color(argb)
        }
    } catch (t: Throwable) {
        Log.w(TAG, "读取壁纸色失败：${t.javaClass.simpleName}: ${t.message}")
        null
    }
}

/**
 * 把种子色规整成能当 primary 用的色调：色相不动，饱和度略收，明度压到与白字
 * （浅色）或深底（深色）保持 4.5:1 对比的位置。
 *
 * 只做「主色」这一层的换算，是为了让 surface / 容器层级继续沿用 [lightScheme] /
 * [darkScheme] 里既有的中性色——那里有明确约定：弹窗和菜单不能跟着主题色染色，
 * 否则会发灰发紫。若整套照搬 M3 生成的 surface，就会破坏这条约定。
 */
internal fun seedToPrimary(seed: Color, dark: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    val hue = hsl[0]
    val sourceSaturation = hsl[1]
    // 近乎灰的壁纸不做强着色，否则会得到一片浑浊的蓝灰。
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
