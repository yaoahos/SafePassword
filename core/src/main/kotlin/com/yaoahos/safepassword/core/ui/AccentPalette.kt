package com.yaoahos.safepassword.core.ui

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 从壁纸像素里挑一个「像这张壁纸」的强调色。
 *
 * 手机端「跟随桌面背景」用它：上层只负责把当前壁纸解成像素数组，判色规则全在这里，
 * 因此可以脱离 Android 单独测。规则：
 *
 * 1. 跳过全透明、近黑（l < 0.08）、近白（l > 0.94）、近灰（s < 0.12）的像素——
 *    夜空、白墙、纯色背景不代表壁纸主色。
 * 2. 按色相分 24 个桶，权重 `s² × (1 - |l - 0.5| × 1.4)`：越饱和、亮度越居中越像主色。
 * 3. 取最重的桶，把左右相邻桶一起按**最短弧**平均——否则 350° 与 5° 会被平均成 177°（青色）。
 * 4. 结果把饱和度抬到 ≥ 0.62、亮度夹进 0.34~0.56，保证浅色与深色主题下都看得清。
 *
 * 纯灰阶壁纸（黑白照、纯色桌面）没有色相可挑，返回中性灰：这时宁可给一个不抢眼的灰，
 * 也不要把灰硬拉成红色。
 */
object AccentPalette {

    private const val BUCKETS = 24
    private const val SPAN = 360.0 / BUCKETS

    /**
     * @param pixels ARGB_8888 像素（`Bitmap.getPixels` 的原样输出），顺序无关
     * @return 不透明 ARGB 色值；全透明或空数组返回 null
     */
    fun fromPixels(pixels: IntArray): Int? {
        if (pixels.isEmpty()) return null

        val weight = DoubleArray(BUCKETS)
        val hueSum = DoubleArray(BUCKETS)
        val satSum = DoubleArray(BUCKETS)
        val lightSum = DoubleArray(BUCKETS)
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        var counted = 0

        for (px in pixels) {
            if ((px ushr 24) and 0xFF < 128) continue
            val r = ((px shr 16) and 0xFF) / 255.0
            val g = ((px shr 8) and 0xFF) / 255.0
            val b = (px and 0xFF) / 255.0
            val (hue, saturation, lightness) = rgbToHsl(r, g, b)

            sumR += r
            sumG += g
            sumB += b
            counted += 1

            if (lightness < 0.08 || lightness > 0.94 || saturation < 0.12) continue
            val w = saturation * saturation * (1 - abs(lightness - 0.5) * 1.4)
            if (w <= 0.0) continue

            val index = min(BUCKETS - 1, (hue / SPAN).toInt())
            weight[index] += w
            hueSum[index] += hue * w
            satSum[index] += saturation * w
            lightSum[index] += lightness * w
        }

        if (counted == 0) return null

        var best = -1
        var bestWeight = 0.0
        for (i in 0 until BUCKETS) {
            if (weight[i] > bestWeight) {
                bestWeight = weight[i]
                best = i
            }
        }

        if (best < 0 || bestWeight <= 0.0) {
            // 整张图都是灰阶：保留灰，只把亮度调到能当强调色的中间区间。
            val (hue, saturation, lightness) = rgbToHsl(sumR / counted, sumG / counted, sumB / counted)
            return toArgb(hue, saturation, min(0.62, max(0.42, lightness)))
        }

        val baseHue = hueSum[best] / weight[best]
        var wSum = 0.0
        var hSum = 0.0
        var sSum = 0.0
        var lSum = 0.0
        for (delta in -1..1) {
            val i = (best + delta + BUCKETS) % BUCKETS
            if (weight[i] <= 0.0) continue
            val bucketHue = hueSum[i] / weight[i]
            var d = bucketHue - baseHue
            if (d > 180) d -= 360
            if (d < -180) d += 360
            hSum += (baseHue + d) * weight[i]
            sSum += satSum[i] * weight[i]
            lSum += lightSum[i] * weight[i]
            wSum += weight[i]
        }
        if (wSum <= 0.0) return null

        return toArgb(
            hSum / wSum,
            min(0.9, max(sSum / wSum, 0.62)),
            min(0.56, max(lSum / wSum, 0.34))
        )
    }

    private fun toArgb(hue: Double, saturation: Double, lightness: Double): Int {
        val (r, g, b) = hslToRgb(((hue % 360) + 360) % 360, saturation, lightness)
        val ri = (r * 255).roundToInt().coerceIn(0, 255)
        val gi = (g * 255).roundToInt().coerceIn(0, 255)
        val bi = (b * 255).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    /** @return (色相 0~360, 饱和度 0~1, 亮度 0~1) */
    private fun rgbToHsl(r: Double, g: Double, b: Double): Triple<Double, Double, Double> {
        val maxc = max(r, max(g, b))
        val minc = min(r, min(g, b))
        val lightness = (maxc + minc) / 2
        if (maxc == minc) return Triple(0.0, 0.0, lightness)

        val d = maxc - minc
        val saturation = if (lightness > 0.5) d / (2 - maxc - minc) else d / (maxc + minc)
        val hue = when (maxc) {
            r -> ((g - b) / d + if (g < b) 6 else 0) * 60
            g -> ((b - r) / d + 2) * 60
            else -> ((r - g) / d + 4) * 60
        }
        return Triple(hue, saturation, lightness)
    }

    private fun hslToRgb(hue: Double, saturation: Double, lightness: Double): Triple<Double, Double, Double> {
        if (saturation == 0.0) return Triple(lightness, lightness, lightness)
        val q = if (lightness < 0.5) lightness * (1 + saturation)
        else lightness + saturation - lightness * saturation
        val p = 2 * lightness - q
        val hk = hue / 360
        return Triple(
            hueToChannel(p, q, hk + 1.0 / 3),
            hueToChannel(p, q, hk),
            hueToChannel(p, q, hk - 1.0 / 3)
        )
    }

    private fun hueToChannel(p: Double, q: Double, t0: Double): Double {
        var t = t0
        if (t < 0) t += 1
        if (t > 1) t -= 1
        return when {
            t < 1.0 / 6 -> p + (q - p) * 6 * t
            t < 1.0 / 2 -> q
            t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
            else -> p
        }
    }
}
