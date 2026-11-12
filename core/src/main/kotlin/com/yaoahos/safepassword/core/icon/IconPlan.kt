package com.yaoahos.safepassword.core.icon

/**
 * 条目图标归一化的决策结果。
 *
 * 这里只做数学，不碰任何 `Bitmap`：尺寸怎么采样、从哪裁、裁多少、结果多大，
 * 全部在纯 JVM 里算好，Android 侧只负责按这份计划执行绘制。
 * 好处是规则本身可以被单测覆盖（大图、长条、极小图这些分支都不需要真机）。
 */
data class IconPlan(
    /** 解码时的 `inSampleSize`，只为省内存，不决定最终清晰度。 */
    val sample: Int,
    /** 按 [sample] 解码后应当得到的宽高。 */
    val srcW: Int,
    val srcH: Int,
    /** 在解码结果上的裁切矩形（像素）。 */
    val cropX: Int,
    val cropY: Int,
    val cropW: Int,
    val cropH: Int,
    /** 内容最终绘制尺寸；等于 [size] 时说明没有留白边。 */
    val outW: Int,
    val outH: Int,
    /** true = 等比装进正方形并留透明边；false = 裁成正方形。 */
    val letterbox: Boolean,
    /** 输出画布边长（正方形画布）。 */
    val size: Int
)

/** 居中裁切适用的宽高比区间：4:3 与 3:4 之间。超出这个范围就改留白。 */
private const val MAX_CROP_RATIO = 4.0 / 3.0
private const val MIN_CROP_RATIO = 3.0 / 4.0

/**
 * 计算图标归一化计划。
 *
 * 规则：
 * 1. 先按 2 的幂粗降采样，让解码后的短边落在 `[size, 2*size)` —— 既不 OOM，
 *    也不会因为降采样把短边压到目标尺寸以下而白白变糊。
 * 2. 宽高比在 4:3 ~ 3:4 之间：居中裁成正方形（切掉的部分很少，看不出来）。
 * 3. 超出这个范围：不裁，等比缩到 [size] 以内，四周补透明边（长条图一裁就废）。
 * 4. 原图比 [size] 小时不放大，避免把小图标拉糊。
 */
fun planIcon(width: Int, height: Int, size: Int = DEFAULT_ICON_SIZE): IconPlan {
    require(size > 0) { "size must be positive" }
    if (width <= 0 || height <= 0) {
        return IconPlan(1, 1, 1, 0, 0, 1, 1, 1, 1, letterbox = false, size = 1)
    }

    // 粗降采样：短边保持在 [size, 2*size)，短边本来就小的不降。
    var sample = 1
    while (minOf(width, height) / (sample * 2) >= size) sample *= 2
    val srcW = (width + sample - 1) / sample
    val srcH = (height + sample - 1) / sample

    val ratio = srcW.toDouble() / srcH.toDouble()
    val cropMode = ratio in MIN_CROP_RATIO..MAX_CROP_RATIO

    if (cropMode) {
        val side = minOf(srcW, srcH)
        val out = minOf(side, size)
        return IconPlan(
            sample = sample,
            srcW = srcW,
            srcH = srcH,
            cropX = (srcW - side) / 2,
            cropY = (srcH - side) / 2,
            cropW = side,
            cropH = side,
            outW = out,
            outH = out,
            letterbox = false,
            size = maxOf(out, 1)
        )
    }

    // 留白：整张图等比缩进 size 以内，短板对齐。
    val longSide = maxOf(srcW, srcH)
    val scale = if (longSide > size) size.toDouble() / longSide.toDouble() else 1.0
    val outW = maxOf(1, (srcW * scale).toInt())
    val outH = maxOf(1, (srcH * scale).toInt())
    return IconPlan(
        sample = sample,
        srcW = srcW,
        srcH = srcH,
        cropX = 0,
        cropY = 0,
        cropW = srcW,
        cropH = srcH,
        outW = outW,
        outH = outH,
        letterbox = true,
        size = maxOf(outW, outH)
    )
}

/** 条目图标的目标边长。56dp 的详情页图标在 3x 屏要 168px，192 留了余量。 */
const val DEFAULT_ICON_SIZE = 192
