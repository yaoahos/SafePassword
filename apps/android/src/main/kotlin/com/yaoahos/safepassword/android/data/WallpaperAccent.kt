package com.yaoahos.safepassword.android.data

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import com.yaoahos.safepassword.core.ui.AccentPalette
import kotlin.math.max

/**
 * 从当前桌面壁纸取一个强调色，供主题页「跟随桌面背景」使用。
 *
 * 只解码到 96px 长边：壁纸动辄 4000×3000（ARGB_8888 约 48MB），原图既没必要也容易 OOM，
 * 而主色统计在 96px 上已经足够稳定。判色规则全在 [AccentPalette] 里，这里只负责拿像素。
 *
 * 优先读壁纸文件（可用 `inSampleSize` 直接降采样，内存最省）；动态壁纸没有文件，退回
 * `WallpaperManager.drawable` 画进 96px 画布。两条路都拿不到就返回 null，由界面提示用户手选颜色。
 */
object WallpaperAccent {

    private const val TARGET = 96

    /** @return 不透明 ARGB 色值；读不到壁纸（动态壁纸、权限异常、解码失败）时 null */
    fun pick(context: Context): Int? {
        val pixels = readFromFile(context) ?: readFromDrawable(context) ?: return null
        return AccentPalette.fromPixels(pixels)
    }

    private fun readFromFile(context: Context): IntArray? = runCatching {
        WallpaperManager.getInstance(context)
            .getWallpaperFile(WallpaperManager.FLAG_SYSTEM)
            ?.use { descriptor ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@use null

                var sample = 1
                while (max(bounds.outWidth, bounds.outHeight) / sample > TARGET) sample *= 2

                val options = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bitmap = BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, options)
                    ?: return@use null
                bitmap.pixels().also { bitmap.recycle() }
            }
    }.getOrNull()

    private fun readFromDrawable(context: Context): IntArray? = runCatching {
        @Suppress("DEPRECATION")
        val drawable = WallpaperManager.getInstance(context).drawable ?: return@runCatching null
        val width = drawable.intrinsicWidth
        val height = drawable.intrinsicHeight
        if (width <= 0 || height <= 0) return@runCatching null

        val scale = TARGET.toFloat() / max(width, height)
        val targetWidth = max(1, (width * scale).toInt())
        val targetHeight = max(1, (height * scale).toInt())
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawable.setBounds(0, 0, targetWidth, targetHeight)
            drawable.draw(this)
        }
        bitmap.pixels().also { bitmap.recycle() }
    }.getOrNull()

    private fun Bitmap.pixels(): IntArray {
        val out = IntArray(width * height)
        getPixels(out, 0, width, 0, 0, width, height)
        return out
    }
}
