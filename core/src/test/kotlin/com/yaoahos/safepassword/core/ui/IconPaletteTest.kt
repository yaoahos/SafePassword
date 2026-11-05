package com.yaoahos.safepassword.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 黄金值测试：这些数值是桌面端 `src/utils.ts` 的 `itemIcon` 算出来的结果。
 * 任何一端改了色板或扰动算法，这里都会红——这正是想要的效果。
 */
class IconPaletteTest {

    @Test
    fun `palette has 17 colours`() {
        assertEquals(17, IconPalette.colors.size)
    }

    @Test
    fun `matches desktop golden values`() {
        assertEquals(0xFFBE123CL, IconPalette.colorFor("GitHub"))
        assertEquals(0xFFB91C1CL, IconPalette.colorFor("支付宝"))
        assertEquals(0xFF4D7C0FL, IconPalette.colorFor("测试"))
        assertEquals(0xFF4D7C0FL, IconPalette.colorFor("招商银行"))
        assertEquals(0xFFA16207L, IconPalette.colorFor("我的密码库"))
        assertEquals(0xFF6D28D9L, IconPalette.colorFor("WeChat"))
        assertEquals(0xFF15803DL, IconPalette.colorFor("gmail"))
    }

    @Test
    fun `same title always maps to same colour`() {
        val first = IconPalette.colorFor("同一台设备")
        repeat(5) { assertEquals(first, IconPalette.colorFor("同一台设备")) }
    }

    @Test
    fun `neighbouring names do not collide`() {
        val a = IconPalette.colorFor("密码1")
        val b = IconPalette.colorFor("密码2")
        val c = IconPalette.colorFor("密码3")
        // 扰动后不应三个都撞在一起
        assertEquals(false, a == b && b == c)
    }
}
