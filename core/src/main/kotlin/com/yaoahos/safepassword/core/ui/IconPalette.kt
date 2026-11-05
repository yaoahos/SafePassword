package com.yaoahos.safepassword.core.ui

/**
 * 条目默认图标（首字符方块）的底色。
 *
 * **与桌面端必须完全一致**：`E:\DSH\SafePasswordDesktop\src\utils.ts` 的
 * `FALLBACK_COLORS` 与 `itemIcon`。色板顺序、取色算法都不可单独改动，
 * 否则同一个标题会在手机上是红色、电脑上是蓝色。
 *
 * 取色只让标题参与（`id` 各端不同），因此同一个标题在任何设备上都得到同一个颜色。
 */
object IconPalette {

    /** 17 个互不相邻的色相，全部取 700 档，白字对比度 4.5~6.5:1。顺序不可变。 */
    val colors: List<Long> = listOf(
        0xFFB91C1C, // 红
        0xFFC2410C, // 橙
        0xFFB45309, // 琥珀
        0xFFA16207, // 金
        0xFF4D7C0F, // 黄绿
        0xFF15803D, // 绿
        0xFF047857, // 翠
        0xFF0F766E, // 青绿
        0xFF0E7490, // 青
        0xFF0369A1, // 天蓝
        0xFF1D4ED8, // 蓝
        0xFF4338CA, // 靛
        0xFF6D28D9, // 紫罗兰
        0xFF7E22CE, // 紫
        0xFFA21CAF, // 品红
        0xFFBE185D, // 玫红
        0xFFBE123C, // 玫瑰
    )

    /** 标题 → 底色。纯算术，无状态，可放心在多处调用。 */
    fun colorFor(title: String): Long {
        // 与 Java String.hashCode 一致（Int 溢出即回绕）
        var h = 0
        for (ch in title) {
            h = h * 31 + ch.code
        }
        // 32 位扰动：把相邻标题打散，避免「密码1/密码2」挨着同一色
        h = h xor (h ushr 16)
        h *= 0x45D9F3B
        h = h xor (h ushr 16)
        return colors[Math.floorMod(h, colors.size)]
    }
}
