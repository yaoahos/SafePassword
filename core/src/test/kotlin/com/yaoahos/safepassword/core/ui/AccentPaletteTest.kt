package com.yaoahos.safepassword.core.ui

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 壁纸取色的规则测试：重点不是「一定能挑出好看的色」，而是几条硬性约束——
 * 灰阶不走色相、跨 0°/360° 不能挑成互补色、透明像素不参与。
 */
class AccentPaletteTest {

    @Test
    fun `empty pixels return null`() {
        assertNull(AccentPalette.fromPixels(IntArray(0)))
    }

    @Test
    fun `fully transparent pixels return null`() {
        val pixels = IntArray(64) { 0x00123456 }
        assertNull(AccentPalette.fromPixels(pixels))
    }

    @Test
    fun `blue wallpaper yields blue accent`() {
        // 七成深蓝 + 两成近黑 + 一成近白，模拟一张以蓝为主的风景壁纸
        val pixels = buildList {
            repeat(70) { add(argb(30, 90, 200)) }
            repeat(20) { add(argb(10, 10, 15)) }
            repeat(10) { add(argb(240, 240, 240)) }
        }.toIntArray()

        val color = colorOf(pixels)
        val (hue, saturation, lightness) = hsl(color)

        assertTrue("色相应落在蓝色区间，实际 $hue", hue in 195.0..235.0)
        assertTrue("饱和度应被抬到可当强调色，实际 $saturation", saturation >= 0.62)
        assertTrue("亮度应被夹进可读区间，实际 $lightness", lightness in 0.34..0.57)
    }

    @Test
    fun `grayscale wallpaper stays neutral`() {
        // 黑白壁纸没有色相，不能硬拉成某个鲜艳色
        val pixels = IntArray(100) { index ->
            if (index % 2 == 0) argb(90, 90, 90) else argb(220, 220, 220)
        }

        val color = AccentPalette.fromPixels(pixels)!!
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        val spread = maxOf(r, maxOf(g, b)) - minOf(r, minOf(g, b))

        assertTrue("灰阶结果不该带明显色偏，实际 RGB=$r,$g,$b", spread <= 8)
        val (_, saturation, lightness) = hsl(color)
        assertTrue("饱和度应保持接近灰，实际 $saturation", saturation <= 0.15)
        assertTrue("亮度应落在可当强调色的区间，实际 $lightness", lightness in 0.42..0.62)
    }

    @Test
    fun `hue buckets merge across the zero boundary`() {
        // 一侧 352°、一侧 8°，平均后应仍是红，而不是跨到 177° 的青色
        val pixels = buildList {
            repeat(40) { add(fromHsl(352.0, 0.8, 0.5)) }
            repeat(40) { add(fromHsl(8.0, 0.8, 0.5)) }
        }.toIntArray()

        val (hue) = hsl(AccentPalette.fromPixels(pixels)!!)
        assertTrue("跨 0° 不应跳到互补色，实际 $hue", hue >= 340.0 || hue <= 20.0)
    }

    @Test
    fun `dominant hue wins over scattered background`() {
        val pixels = buildList {
            repeat(60) { add(fromHsl(150.0, 0.7, 0.45)) }
            repeat(20) { add(fromHsl(20.0, 0.7, 0.45)) }
            repeat(20) { add(argb(245, 245, 245)) }
        }.toIntArray()

        val (hue) = hsl(AccentPalette.fromPixels(pixels)!!)
        assertTrue("应以占比最高的绿色为主，实际 $hue", hue in 130.0..170.0)
    }

    // ---- helpers ----

    private fun colorOf(pixels: IntArray): Int = AccentPalette.fromPixels(pixels)!!

    private fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    private fun hsl(color: Int): Triple<Double, Double, Double> {
        val r = ((color shr 16) and 0xFF) / 255.0
        val g = ((color shr 8) and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0
        val maxc = maxOf(r, maxOf(g, b))
        val minc = minOf(r, minOf(g, b))
        val lightness = (maxc + minc) / 2
        if (maxc == minc) return Triple(0.0, 0.0, lightness)
        val d = maxc - minc
        val saturation = if (lightness > 0.5) d / (2 - maxc - minc) else d / (maxc + minc)
        val hue = when (maxc) {
            r -> ((g - b) / d + if (g < b) 6 else 0) * 60
            g -> ((b - r) / d + 2) * 60
            else -> ((r - g) / d + 4) * 60
        }
        return Triple(((hue % 360) + 360) % 360, saturation, lightness)
    }

    private fun fromHsl(hue: Double, saturation: Double, lightness: Double): Int {
        val q = if (lightness < 0.5) lightness * (1 + saturation)
        else lightness + saturation - lightness * saturation
        val p = 2 * lightness - q
        fun channel(t0: Double): Double {
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
        val hk = hue / 360
        val r = (channel(hk + 1.0 / 3) * 255).toInt().coerceIn(0, 255)
        val g = (channel(hk) * 255).toInt().coerceIn(0, 255)
        val b = (channel(hk - 1.0 / 3) * 255).toInt().coerceIn(0, 255)
        return argb(r, g, b)
    }
}
