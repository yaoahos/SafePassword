package com.yaoahos.safepassword.core.qr

import com.google.zxing.BarcodeFormat
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.yaoahos.safepassword.core.security.Totp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 扫码链路的纯 JVM 验证：用真实二维码合成「相册像素」与「相机 YUV 帧」两种输入，
 * 覆盖行填充、旋转、截断这几个真机上最容易出问题的分支。
 *
 * 背景：相册识别能出结果、实时扫码却没反应时，第一嫌疑就是 Y 平面的 rowStride 处理或
 * 旋转角度算反；这两点在真机上很难肉眼判断，只能用合成帧钉住。
 */
class QrDecoderTest {

    private val otpauth = "otpauth://totp/GitHub:test?secret=JBSWY3DPEHPK3PXP&issuer=GitHub"

    @Test
    fun `相册路径 从 ARGB 像素解出原文`() {
        val pixels = scenePixels(qrMatrix(), 360, 360)
        val text = QrDecoder.decodePixels(QrDecoder.newReader(), pixels, 360, 360)
        assertEquals(otpauth, text)
    }

    @Test
    fun `相机路径 行跨度等于宽度时不带填充`() {
        val pixels = scenePixels(qrMatrix(), 360, 360)
        val luma = yuvScene(pixels, 360, 360, rowStride = 360)
        val text = QrDecoder.decodeYuv(
            QrDecoder.newReader(), luma, luma.size, 360, 360, rowStride = 360,
            pixelStride = 1, rotationDegrees = 0
        )
        assertEquals(otpauth, text)
    }

    @Test
    fun `相机路径 行尾有填充字节时逐行剥离`() {
        val pixels = scenePixels(qrMatrix(), 360, 360)
        // 真机上 Y 平面的 rowStride 常常比 width 大 16~64 字节，直接按 width 切行会把图切乱。
        val luma = yuvScene(pixels, 360, 360, rowStride = 360 + 48)
        val text = QrDecoder.decodeYuv(
            QrDecoder.newReader(), luma, luma.size, 360, 360, rowStride = 360 + 48,
            pixelStride = 1, rotationDegrees = 0
        )
        assertEquals(otpauth, text)
    }

    @Test
    fun `相机路径 传感器横向帧按 rotationDegrees 摆正`() {
        // 竖屏 300x400 的「屏幕画面」，传感器给的是横向 400x300 且内容反向转了 90°。
        val upright = scenePixels(qrMatrix(), 300, 400)
        val (rotated, rw, rh) = rotateCcw(upright, 300, 400)
        val luma = yuvScene(rotated, rw, rh, rowStride = rw + 32)
        val text = QrDecoder.decodeYuv(
            QrDecoder.newReader(), luma, luma.size, rw, rh, rowStride = rw + 32,
            pixelStride = 1, rotationDegrees = 90
        )
        assertEquals(otpauth, text)
    }

    @Test
    fun `解出的原文能直接喂给 Totp 解析`() {
        val pixels = scenePixels(qrMatrix(), 360, 360)
        val text = QrDecoder.decodePixels(QrDecoder.newReader(), pixels, 360, 360)
        assertNotNull(Totp.parse(text!!))
    }

    @Test
    fun `没有码的画面返回 null 而不是抛异常`() {
        val blank = IntArray(360 * 360) { 0xFFFFFFFF.toInt() }
        assertNull(QrDecoder.decodePixels(QrDecoder.newReader(), blank, 360, 360))
        val luma = yuvScene(blank, 360, 360, rowStride = 360)
        assertNull(
            QrDecoder.decodeYuv(
                QrDecoder.newReader(), luma, luma.size, 360, 360, 360, 1, 0
            )
        )
    }

    @Test
    fun `帧被截断时不崩`() {
        val pixels = scenePixels(qrMatrix(), 360, 360)
        val luma = yuvScene(pixels, 360, 360, rowStride = 360)
        assertNull(
            QrDecoder.decodeYuv(
                QrDecoder.newReader(), luma, luma.size / 3, 360, 360, 360, 1, 0
            )
        )
    }

    // ------------------------------------------------------------------ 合成工具

    private fun qrMatrix(size: Int = 300): BitMatrix =
        QRCodeWriter().encode(otpauth, BarcodeFormat.QR_CODE, size, size)

    /** 白底、二维码居中，模拟一张屏幕截图。 */
    private fun scenePixels(matrix: BitMatrix, width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height) { 0xFFFFFFFF.toInt() }
        val ox = (width - matrix.width) / 2
        val oy = (height - matrix.height) / 2
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                val value = if (matrix.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                pixels[(oy + y) * width + (ox + x)] = value
            }
        }
        return pixels
    }

    /** 把像素转成 Y 平面，并留出 [rowStride] - width 的行尾填充。 */
    private fun yuvScene(pixels: IntArray, width: Int, height: Int, rowStride: Int): ByteArray {
        val buffer = ByteArray(rowStride * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = pixels[y * width + x]
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                buffer[y * rowStride + x] = ((r * 299 + g * 587 + b * 114) / 1000).toByte()
            }
        }
        return buffer
    }

    /** 逆时针转 90°，模拟「竖着看是正的、传感器里是横的」。 */
    private fun rotateCcw(pixels: IntArray, width: Int, height: Int): Triple<IntArray, Int, Int> {
        val newWidth = height
        val newHeight = width
        val out = IntArray(newWidth * newHeight)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val nx = height - 1 - y
                val ny = x
                out[ny * newWidth + nx] = pixels[y * width + x]
            }
        }
        return Triple(out, newWidth, newHeight)
    }
}
