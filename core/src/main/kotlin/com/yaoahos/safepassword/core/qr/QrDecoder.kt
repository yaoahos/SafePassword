package com.yaoahos.safepassword.core.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * 二维码 / 条码解码。
 *
 * 只依赖 zxing core（纯 Java），不碰 Android API，因此可以用真实合成帧做单元测试：
 * 相机给的是 YUV_420_888 的 Y 平面（可能带行填充、可能旋转 90°），相册给的是 ARGB 像素，
 * 两条路径最终都归到同一个 [LuminanceSource] → [HybridBinarizer] → [MultiFormatReader] 流程。
 */
object QrDecoder {

    /** 只认这几种码：动态验证码密钥基本都是 QR，偶尔是 Data Matrix 或条形码。 */
    val HINTS: Map<DecodeHintType, Any> = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(
            BarcodeFormat.QR_CODE,
            BarcodeFormat.DATA_MATRIX,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39
        ),
        DecodeHintType.TRY_HARDER to true
    )

    /**
     * 建一个已带 hints 的 reader。
     *
     * MultiFormatReader 不是线程安全的，连续解码（相机分析线程）时复用同一个实例，
     * 相册识别这类一次性场景直接用 [decodePixels] 里的临时实例即可。
     */
    fun newReader(): MultiFormatReader = MultiFormatReader().apply { setHints(HINTS) }

    /**
     * 从 YUV_420_888 的 Y 平面解码。
     *
     * @param data 帧数据（调用方可复用缓冲区，所以长度可能大于 [dataLength]）
     * @param dataLength 本帧真实字节数
     * @param rowStride Y 平面每行的跨度（可能大于 [width]，行尾有填充）
     * @param pixelStride 相邻像素间隔（Y 平面正常为 1）
     * @param rotationDegrees 顺时针摆正所需角度，与 CameraX 的 `imageInfo.rotationDegrees` 一致
     */
    fun decodeYuv(
        reader: MultiFormatReader,
        data: ByteArray,
        dataLength: Int,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int,
        rotationDegrees: Int
    ): String? {
        if (width <= 0 || height <= 0 || dataLength <= 0) return null
        val luma = toLuma(data, dataLength, width, height, rowStride, pixelStride)
        var source: LuminanceSource =
            PlanarYUVLuminanceSource(luma, width, height, 0, 0, width, height, false)
        // rotationDegrees 是「顺时针转多少度能摆正」，ZXing 只提供逆时针旋转。
        repeat(rotationSteps(rotationDegrees)) {
            if (source.isRotateSupported) source = source.rotateCounterClockwise()
        }
        return decode(reader, source)
    }

    /** 从相册图片的 ARGB 像素解码。 */
    fun decodePixels(
        reader: MultiFormatReader,
        pixels: IntArray,
        width: Int,
        height: Int
    ): String? {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return null
        return decode(reader, RGBLuminanceSource(width, height, pixels))
    }

    private fun decode(reader: MultiFormatReader, source: LuminanceSource): String? =
        runCatching { reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text }.getOrNull()

    /**
     * 把带行填充的 Y 平面压成 width×height 的连续数组。
     *
     * 直接按 width 切行会在有填充的设备上把整幅图解码成一团噪声——这是「相机预览正常、
     * 却怎么都扫不出来」最常见的原因。
     */
    private fun toLuma(
        data: ByteArray,
        dataLength: Int,
        width: Int,
        height: Int,
        rowStride: Int,
        pixelStride: Int
    ): ByteArray {
        if (pixelStride == 1 && rowStride == width) {
            val size = width * height
            return when {
                dataLength == size -> data
                dataLength > size -> data.copyOf(size)
                // 帧被截断时补零，宁可解不出来也不要让 LuminanceSource 抛参数异常。
                else -> ByteArray(size).also { data.copyInto(it, 0, 0, dataLength) }
            }
        }
        val out = ByteArray(width * height)
        val stride = if (rowStride <= 0) width else rowStride
        val step = if (pixelStride <= 0) 1 else pixelStride
        for (y in 0 until height) {
            val rowStart = y * stride
            if (rowStart >= dataLength) break
            for (x in 0 until width) {
                val index = rowStart + x * step
                if (index >= dataLength) break
                out[y * width + x] = data[index]
            }
        }
        return out
    }

    private fun rotationSteps(degrees: Int): Int = when (((degrees % 360) + 360) % 360) {
        90 -> 3
        180 -> 2
        270 -> 1
        else -> 0
    }
}
