package com.yaoahos.safepassword.android.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.util.Base64
import com.yaoahos.safepassword.core.icon.DEFAULT_ICON_SIZE
import com.yaoahos.safepassword.core.icon.planIcon
import java.io.ByteArrayOutputStream

/**
 * 条目图标归一化：把任意图片收敛成统一的裸 base64（PNG 或 WebP，按体积择优）。
 *
 * 规则见 [planIcon]（正方形居中裁切、长条等比留白、不放大、按 2 的幂降采样）。
 * 这里只负责按计划做 Bitmap 操作，两条写入路径共用：
 * - 用户手动选图：[encodeIcon]
 * - 外部卡片导入：[normalizeIconData]
 *
 * 编码格式不是固定的，因为两类图标的体积特性正好相反（实测 192px）：
 * - 图形类（纯色块、透明 logo、渐变文字）：PNG 900B ~ 1.1KB，WebP 反而要 1.8KB
 * - 照片类（高频细节）：PNG 88KB，WebP q85 只要 14KB，差 6 倍
 *
 * 所以策略是「先 PNG，体积不大就用 PNG；大了再编一份 WebP 取小者」。
 * 图形类永远不会走到第二步，照片类自动落到 WebP，两边都不吃亏。
 * 读取侧无需区分：`BitmapFactory` 与浏览器 `<img>` 都能靠魔数自动识别。
 */
internal object IconCodec {

    /** 归一化目标边长，与 `:core` 的默认值保持一致。 */
    private const val TARGET = DEFAULT_ICON_SIZE

    /** PNG 已经小于这个体积就没必要试 WebP 了（logo 类通常 1KB 上下）。 */
    private const val PNG_KEEP_BYTES = 16 * 1024

    /** 择优后仍超过这个体积，说明细节过多，尺寸再降一档重来。 */
    private const val FALLBACK_BYTES = 28 * 1024
    private const val FALLBACK_TARGET = 128

    /** 有损 WebP 的质量：192px 小图上肉眼难辨，体积比 q95 再省四分之一。 */
    private const val WEBP_QUALITY = 85

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

    /** 解码 → 裁切/留白 → 择优编码 → base64；任何一步失败返回 null。 */
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

        val encoded = encodeSmallest(output)
        if (output !== cropped && output !== decoded) output.recycle()
        if (cropped !== decoded) cropped.recycle()
        decoded.recycle()
        if (encoded.isEmpty()) return null

        if (encoded.size > FALLBACK_BYTES && target > FALLBACK_TARGET) {
            // 细节实在太多（照片类）：尺寸再降一档，宁可略软也别让库臃肿。
            return render(source, FALLBACK_TARGET)
        }
        return Base64.encodeToString(encoded, Base64.NO_WRAP)
    }

    /**
     * PNG 与 WebP 各编一次，取体积小的那个。
     *
     * PNG 已经够小就直接返回，省掉一次 WebP 编码（图形类图标占多数，这步省的是常态开销）。
     */
    private fun encodeSmallest(bitmap: Bitmap): ByteArray {
        val png = compress(bitmap, Bitmap.CompressFormat.PNG, 100)
        if (png != null && png.size <= PNG_KEEP_BYTES) return png
        val webp = compress(bitmap, webpFormat(), WEBP_QUALITY)
        return when {
            png == null -> webp ?: ByteArray(0)
            webp == null -> png
            webp.size < png.size -> webp
            else -> png
        }
    }

    private fun compress(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray? {
        val stream = ByteArrayOutputStream()
        val ok = runCatching { bitmap.compress(format, quality, stream) }.getOrDefault(false)
        return if (ok && stream.size() > 0) stream.toByteArray() else null
    }

    /**
     * API 30 起 `WEBP` 被拆成 `WEBP_LOSSY` / `WEBP_LOSSLESS`，旧常量仍可用但已标记弃用。
     * 这里按版本分流，避免在低版本上调用不存在的常量。
     */
    @Suppress("DEPRECATION")
    private fun webpFormat(): Bitmap.CompressFormat =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bitmap.CompressFormat.WEBP_LOSSY
        } else {
            Bitmap.CompressFormat.WEBP
        }
}
