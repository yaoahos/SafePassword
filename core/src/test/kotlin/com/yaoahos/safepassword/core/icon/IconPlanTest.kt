package com.yaoahos.safepassword.core.icon

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 图标归一化计划：覆盖正方形、横竖长条、4:3 边界、超大图采样与极小图不放大。
 * 这些都是纯数学分支，不需要真实图片。
 */
class IconPlanTest {

    private val size = 192

    @Test
    fun square_exact_target_is_not_scaled() {
        val plan = planIcon(192, 192, size)
        assertEquals(1, plan.sample)
        assertEquals(192, plan.srcW)
        assertEquals(192, plan.srcH)
        assertFalse(plan.letterbox)
        assertEquals(0, plan.cropX)
        assertEquals(0, plan.cropY)
        assertEquals(192, plan.cropW)
        assertEquals(192, plan.outW)
        assertEquals(192, plan.size)
    }

    @Test
    fun square_larger_than_target_is_downsampled() {
        // 256 → 短边 256，256/(1*2)=128 < 192，所以不降采样，直接缩到 192。
        val plan256 = planIcon(256, 256, size)
        assertEquals(1, plan256.sample)
        assertEquals(192, plan256.outW)

        // 400 → 400/2=200 >= 192 降一次，200/4=100 < 192 停，sample=2，解码后 200 再缩到 192。
        val plan400 = planIcon(400, 400, size)
        assertEquals(2, plan400.sample)
        assertEquals(200, plan400.srcW)
        assertEquals(192, plan400.outW)
    }

    @Test
    fun small_square_is_not_upscaled() {
        val plan = planIcon(48, 48, size)
        assertEquals(1, plan.sample)
        assertEquals(48, plan.outW)
        assertEquals(48, plan.outH)
        assertEquals(48, plan.size)
        assertFalse(plan.letterbox)
    }

    @Test
    fun wide_banner_uses_letterbox() {
        // 1200×400 是 3:1，超出 4:3，不裁，缩到 192 宽、64 高，画布仍是 192。
        val plan = planIcon(1200, 400, size)
        assertEquals(2, plan.sample)
        assertEquals(600, plan.srcW)
        assertEquals(200, plan.srcH)
        assertTrue(plan.letterbox)
        assertEquals(192, plan.outW)
        assertEquals(64, plan.outH)
        assertEquals(192, plan.size)
        // 留白模式不裁任何像素。
        assertEquals(0, plan.cropX)
        assertEquals(600, plan.cropW)
        assertEquals(200, plan.cropH)
    }

    @Test
    fun tall_banner_uses_letterbox() {
        val plan = planIcon(400, 1200, size)
        assertTrue(plan.letterbox)
        assertEquals(64, plan.outW)
        assertEquals(192, plan.outH)
        assertEquals(192, plan.size)
    }

    @Test
    fun extreme_aspect_ratio_still_fits_canvas() {
        // 8:1 竖条：缩到 24×192，宽不变成 0。
        val plan = planIcon(100, 800, size)
        assertTrue(plan.letterbox)
        assertEquals(24, plan.outW)
        assertEquals(192, plan.outH)
        assertEquals(192, plan.size)
    }

    @Test
    fun four_by_three_is_cropped_not_letterboxed() {
        // 4:3 正好在上界，应当居中裁切，而不是留白。
        val plan = planIcon(256, 192, size)
        assertFalse(plan.letterbox)
        assertEquals(1, plan.sample)
        assertEquals(192, plan.cropW)
        assertEquals(192, plan.cropH)
        assertEquals(32, plan.cropX)
        assertEquals(0, plan.cropY)
        assertEquals(192, plan.outW)
    }

    @Test
    fun three_by_four_is_cropped_not_letterboxed() {
        val plan = planIcon(192, 256, size)
        assertFalse(plan.letterbox)
        assertEquals(0, plan.cropX)
        assertEquals(32, plan.cropY)
        assertEquals(192, plan.cropH)
    }

    @Test
    fun slightly_over_boundary_switches_to_letterbox() {
        // 2:3 超出下界 3:4 = 0.75，改留白：128×192。
        val plan = planIcon(200, 300, size)
        assertTrue(plan.letterbox)
        assertEquals(128, plan.outW)
        assertEquals(192, plan.outH)
        assertEquals(192, plan.size)
    }

    @Test
    fun huge_photo_is_downsampled_by_power_of_two() {
        // 4000×3000：短边 3000，逐次减半到 375 才落在 [192, 384)，sample=8。
        val plan = planIcon(4000, 3000, size)
        assertEquals(8, plan.sample)
        assertEquals(500, plan.srcW)
        assertEquals(375, plan.srcH)
        assertFalse(plan.letterbox)
        // 4:3 → 居中裁成 375 的方块，左右各切 62。
        assertEquals(375, plan.cropW)
        assertEquals(62, plan.cropX)
        assertEquals(192, plan.outW)
    }

    @Test
    fun invalid_size_falls_back_to_one_pixel() {
        val plan = planIcon(0, 500, size)
        assertEquals(1, plan.size)
        assertEquals(1, plan.outW)
    }
}
