package com.yaoahos.safepassword.core.icon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图标归一化计划：覆盖正方形、横竖长条、极端比例、超大图采样与极小图不放大。
 * 1.0.11 起规则简化为「任意比例一律居中裁成正方形」，这些用例重点守住
 * 「输出永远是正方形」和「长条不再留白」两条，纯数学分支不需要真实图片。
 */
class IconPlanTest {

    private val size = 192

    @Test
    fun square_exact_target_is_not_scaled() {
        val plan = planIcon(192, 192, size)
        assertEquals(1, plan.sample)
        assertEquals(192, plan.srcW)
        assertEquals(192, plan.srcH)
        assertEquals(0, plan.cropX)
        assertEquals(0, plan.cropY)
        assertEquals(192, plan.cropW)
        assertEquals(192, plan.cropH)
        assertEquals(192, plan.size)
    }

    @Test
    fun square_larger_than_target_is_downsampled() {
        // 256 → 短边 256，256/(1*2)=128 < 192，所以不降采样，直接缩到 192。
        val plan256 = planIcon(256, 256, size)
        assertEquals(1, plan256.sample)
        assertEquals(192, plan256.size)

        // 400 → 400/2=200 >= 192 降一次，200/4=100 < 192 停，sample=2，解码后 200 再缩到 192。
        val plan400 = planIcon(400, 400, size)
        assertEquals(2, plan400.sample)
        assertEquals(200, plan400.srcW)
        assertEquals(192, plan400.size)
    }

    @Test
    fun small_square_is_not_upscaled() {
        val plan = planIcon(48, 48, size)
        assertEquals(1, plan.sample)
        assertEquals(48, plan.size)
        assertEquals(48, plan.cropW)
        assertEquals(48, plan.cropH)
    }

    @Test
    fun three_to_one_banner_is_cropped() {
        // 1200×400 是 3:1：以前按阈值留白，现在也裁成正方形。
        val plan = planIcon(1200, 400, size)
        assertEquals(2, plan.sample)
        assertEquals(600, plan.srcW)
        assertEquals(200, plan.srcH)
        // 短边 200 是裁切边长，左右各切 200。
        assertEquals(200, plan.cropX)
        assertEquals(0, plan.cropY)
        assertEquals(200, plan.cropW)
        assertEquals(200, plan.cropH)
        assertEquals(192, plan.size)
    }

    @Test
    fun one_to_three_banner_is_cropped() {
        val plan = planIcon(400, 1200, size)
        assertEquals(0, plan.cropX)
        assertEquals(200, plan.cropY)
        assertEquals(200, plan.cropW)
        assertEquals(200, plan.cropH)
        assertEquals(192, plan.size)
    }

    @Test
    fun sixteen_by_nine_photo_is_cropped() {
        // 手机照片最常见的比例：必须裁成正方形，否则用户会认为「没转成正方形」。
        val plan = planIcon(1920, 1080, size)
        assertEquals(4, plan.sample)
        assertEquals(480, plan.srcW)
        assertEquals(270, plan.srcH)
        assertEquals(105, plan.cropX)
        assertEquals(270, plan.cropW)
        assertEquals(270, plan.cropH)
        assertEquals(192, plan.size)
    }

    @Test
    fun four_to_one_banner_is_cropped_not_letterboxed() {
        // 4:1 曾因「超出 3:1 阈值」走留白，现在一样居中裁成正方形。
        val plan = planIcon(800, 200, size)
        assertEquals(300, plan.cropX)
        assertEquals(200, plan.cropW)
        assertEquals(200, plan.cropH)
        assertEquals(192, plan.size)
    }

    @Test
    fun extreme_aspect_ratio_is_still_square() {
        // 8:1 竖条：只取中间 100×100，输出仍是正方形，宽度不会变成 0。
        val plan = planIcon(100, 800, size)
        assertEquals(100, plan.cropW)
        assertEquals(100, plan.cropH)
        assertEquals(350, plan.cropY)
        assertEquals(100, plan.size)
    }

    @Test
    fun four_by_three_is_cropped() {
        val plan = planIcon(256, 192, size)
        assertEquals(1, plan.sample)
        assertEquals(192, plan.cropW)
        assertEquals(192, plan.cropH)
        assertEquals(32, plan.cropX)
        assertEquals(0, plan.cropY)
        assertEquals(192, plan.size)
    }

    @Test
    fun three_by_four_is_cropped() {
        val plan = planIcon(192, 256, size)
        assertEquals(0, plan.cropX)
        assertEquals(32, plan.cropY)
        assertEquals(192, plan.cropW)
        assertEquals(192, plan.cropH)
    }

    @Test
    fun two_by_three_is_cropped() {
        // 2:3 曾落在更早的 3:4 阈值之外而走留白，现在应当裁切。
        val plan = planIcon(200, 300, size)
        assertEquals(0, plan.cropX)
        assertEquals(50, plan.cropY)
        assertEquals(200, plan.cropW)
        assertEquals(200, plan.cropH)
        assertEquals(192, plan.size)
    }

    @Test
    fun huge_photo_is_downsampled_by_power_of_two() {
        // 4000×3000：短边 3000，逐次减半到 375 才落在 [192, 384)，sample=8。
        val plan = planIcon(4000, 3000, size)
        assertEquals(8, plan.sample)
        assertEquals(500, plan.srcW)
        assertEquals(375, plan.srcH)
        // 居中裁成 375 的方块，左右各切 62。
        assertEquals(375, plan.cropW)
        assertEquals(62, plan.cropX)
        assertEquals(192, plan.size)
    }

    @Test
    fun every_aspect_ratio_produces_a_square() {
        // 回归用例：不管传入什么宽高，裁切矩形与输出画布都必须是正方形。
        val cases = listOf(
            1 to 1, 3 to 2, 16 to 9, 2 to 3, 4 to 1, 1 to 4, 8 to 1, 1 to 8, 100 to 1
        )
        for ((w, h) in cases) {
            val plan = planIcon(w * 240, h * 240, size)
            assertEquals("cropW != cropH for $w:$h", plan.cropW, plan.cropH)
            assertTrue("size must be positive for $w:$h", plan.size > 0)
            assertTrue("size exceeds target for $w:$h", plan.size <= size)
            assertTrue("crop X out of bounds for $w:$h", plan.cropX + plan.cropW <= plan.srcW)
            assertTrue("crop Y out of bounds for $w:$h", plan.cropY + plan.cropH <= plan.srcH)
        }
    }

    @Test
    fun invalid_size_falls_back_to_one_pixel() {
        val plan = planIcon(0, 500, size)
        assertEquals(1, plan.size)
        assertEquals(1, plan.cropW)
        assertEquals(1, plan.cropH)
    }
}
