package com.yaoahos.safepassword.core.icon

/**
 * 条目图标归一化的决策结果。
 *
 * 这里只做数学，不碰任何 `Bitmap`：尺寸怎么采样、从哪裁、裁多少、结果多大，
 * 全部在纯 JVM 里算好，Android 侧只负责按这份计划执行绘制。
 * 好处是规则本身可以被单测覆盖（大图、长条、极小图这些分支都不需要真机）。
 *
 * 输出**永远是正方形**：坐标按解码后的短边居中裁一个正方形，再等比缩到 [size]。
 */
data class IconPlan(
    /** 解码时的 `inSampleSize`，只为省内存，不决定最终清晰度。 */
    val sample: Int,
    /** 按 [sample] 解码后应当得到的宽高。 */
    val srcW: Int,
    val srcH: Int,
    /** 在解码结果上的裁切矩形（像素），恒为正方形。 */
    val cropX: Int,
    val cropY: Int,
    val cropW: Int,
    val cropH: Int,
    /** 输出正方形边长；内容等比缩放到这个尺寸并铺满画布。 */
    val size: Int
)

/**
 * 计算图标归一化计划。
 *
 * 规则：
 * 1. 先按 2 的幂粗降采样，让解码后的短边落在 `[size, 2*size)` —— 既不 OOM，
 *    也不会因为降采样把短边压到目标尺寸以下而白白变糊。
 * 2. **任意宽高比一律居中裁成正方形**。1.0.9 曾对长条图等比留白：画布虽然是
 *    正方形，内容却还是一条横幅/竖条，用户看到的就是「长方形图标没转换成正方形」；
 *    1.0.11 起取消留白。极端横幅只会保留中间一块，与头像裁切的常规做法一致
 *    （图标最终只有 56dp，保留整张长图反而什么都看不清）。
 * 3. 原图比 [size] 小时不放大，避免把小图标拉糊。
 */
fun planIcon(width: Int, height: Int, size: Int = DEFAULT_ICON_SIZE): IconPlan {
    require(size > 0) { "size must be positive" }
    if (width <= 0 || height <= 0) {
        return IconPlan(1, 1, 1, 0, 0, 1, 1, 1)
    }

    // 粗降采样：短边保持在 [size, 2*size)，短边本来就小的不降。
    var sample = 1
    while (minOf(width, height) / (sample * 2) >= size) sample *= 2
    val srcW = (width + sample - 1) / sample
    val srcH = (height + sample - 1) / sample

    val side = minOf(srcW, srcH)
    return IconPlan(
        sample = sample,
        srcW = srcW,
        srcH = srcH,
        cropX = (srcW - side) / 2,
        cropY = (srcH - side) / 2,
        cropW = side,
        cropH = side,
        size = maxOf(minOf(side, size), 1)
    )
}

/** 条目图标的目标边长。56dp 的详情页图标在 3x 屏要 168px，192 留了余量。 */
const val DEFAULT_ICON_SIZE = 192
