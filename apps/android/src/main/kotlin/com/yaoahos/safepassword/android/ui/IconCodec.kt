package com.yaoahos.safepassword.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.util.Base64
import com.yaoahos.safepassword.core.icon.DEFAULT_ICON_SIZE
import com.yaoahos.safepassword.core.icon.planIcon
import java.io.ByteArrayOutputStream

/**
 * 条目图标归一化：把任意图片收敛成统一的 PNG 裸 base64。
 *
 * 规则见 [planIcon]（正方形居中裁切、长条等比留白、不放大、按 2 的幂降采样）。
 * 这里只负责按计划做 Bitmap 操作，两条写入路径共用：
 * - 用户手动选图：[encodeIcon]
 * - 外部卡片导入：[normalizeIconData]
 *
 * 体积回落：编码后超过 [FALLBACK_BYTES] 的（典型是照片类图标），
 * 再按 128 重编一次。logo 类在 192 只有 1~2 KB，永远不会触发。
 */
internal object IconCodec {

    /** 归一化目标边长，与 `:core` 的默认值保持一致。 */
    private const val TARGET = DEFAULT_ICON_SIZE

    /** 超过这个字节数就降到 [FALLBACK_TARGET] 重编，控制库体积。 */
    private const val FALLBACK_BYTES = 48 * 1024
    private const val FALLBACK_TARGET = 128

    /** 用户手动选图：读不到、解不开都返回带文案的失败。 */
    fun encodeIcon(context: Context, uri: Uri): Result<String> {
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull() ?: return Result.failure(IllegalArgumentException("无法读取图片"))
        val encoded = render(bytes, TARGET)
            ?: return Result.failure(IllegalArgumentException("图片尺寸无效"))
        return Result.success(encoded)
    }

    /**
     * 外部卡片导入的图标：解不开就原样返回，不要因此丢掉整张卡片。
     * 与旧实现一致——失败时保留原串，交给显示层兜底。
     */
    fun normalizeIconData(raw: String): String {
        if (raw.isBlank()) return ""
        val clean = raw.substringAfter("base64,", raw).filterNot { it.isWhitespace() }
        if (clean.isBlank()) return ""
        val bytes = runCatching { Base64.decode(clean, Base64.DEFAULT) }.getOrNull() ?: return clean
        return render(bytes, TARGET) ?: clean
    }

    /** 解码 → 裁切/留白 → PNG → base64；任何一步失败返回 null。 */
    private fun render(source: ByteArray, target: Int): String? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        // 第一次 plan 只为了拿降采样倍率，避免超大图整张进内存。
        val probe = planIcon(bounds.outWidth, bounds.outHeight, target)
        val decoded = runCatching {
            BitmapFactory.decodeByteArray(
                source,
                0,
                source.size,
                BitmapFactory.Options().apply { inSampleSize = probe.sample }
            )
        }.getOrNull() ?: return null

        // 解码器的实际输出尺寸可能和推算值差 1 像素，按真实尺寸重新出计划。
        val plan = planIcon(decoded.width, decoded.height, target)
        val cropped = if (plan.cropW == decoded.width && plan.cropH == decoded.height) {
            decoded
        } else {
            Bitmap.createBitmap(decoded, plan.cropX, plan.cropY, plan.cropW, plan.cropH)
        }

        val output = if (plan.letterbox) {
            // 留白：正方形透明画布，内容居中。
            val canvasBitmap = Bitmap.createBitmap(plan.size, plan.size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(canvasBitmap)
            val scaled = Bitmap.createScaledBitmap(cropped, plan.outW, plan.outH, true)
            canvas.drawBitmap(
                scaled,
                (plan.size - plan.outW) / 2f,
                (plan.size - plan.outH) / 2f,
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
            if (scaled !== cropped) scaled.recycle()
            canvasBitmap
        } else if (plan.outW == cropped.width && plan.outH == cropped.height) {
            cropped
        } else {
            Bitmap.createScaledBitmap(cropped, plan.outW, plan.outH, true)
        }

        val stream = ByteArrayOutputStream()
        val ok = output.compress(Bitmap.CompressFormat.PNG, 100, stream)
        if (output !== cropped && output !== decoded) output.recycle()
        if (cropped !== decoded) cropped.recycle()
        decoded.recycle()
        if (!ok || stream.size() == 0) return null

        val png = stream.toByteArray()
        if (png.size > FALLBACK_BYTES && target > FALLBACK_TARGET) {
            // 照片类图标：降到 128 再编一次，体积能省一半以上。
            return render(source, FALLBACK_TARGET)
        }
        return Base64.encodeToString(png, Base64.NO_WRAP)
    }
}
