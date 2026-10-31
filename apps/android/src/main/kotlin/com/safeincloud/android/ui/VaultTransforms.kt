package com.safeincloud.android.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.safeincloud.core.io.SafeInCloudCard
import com.safeincloud.core.model.Label
import com.safeincloud.core.model.Vault
import com.safeincloud.core.model.newId
import java.io.ByteArrayOutputStream

/**
 * 与 ViewModel 状态无关的纯变换。
 *
 * 放在这里而不是 AppViewModel 内，是为了让条目处理逻辑可以被单独阅读和复用，
 * 也让 AppViewModel 只保留状态编排职责。
 */

/** 把外部文件名收敛成本机安全的名字。 */
internal fun sanitizeFileName(name: String): String =
    name.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(80).ifBlank { "attachment" }

/** 把导入的 base64 图标压到 128px PNG，避免密码库被图标撑大。 */
internal fun compactIconData(raw: String): String {
    if (raw.isBlank()) return ""
    val clean = raw.substringAfter("base64,", raw).filterNot { it.isWhitespace() }
    if (clean.isBlank()) return ""
    return runCatching {
        val bytes = Base64.decode(clean, Base64.DEFAULT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching clean
        var sample = 1
        while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) {
            sample *= 2
        }
        val bitmap = BitmapFactory.decodeByteArray(
            bytes,
            0,
            bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return@runCatching clean
        val scaled = if (bitmap.width > 128 || bitmap.height > 128) {
            Bitmap.createScaledBitmap(bitmap, 128, 128, true)
        } else {
            bitmap
        }
        val output = ByteArrayOutputStream()
        val compressed = scaled.compress(Bitmap.CompressFormat.PNG, 100, output)
        if (scaled !== bitmap) scaled.recycle()
        bitmap.recycle()
        if (!compressed || output.size() == 0) {
            clean
        } else {
            val encoded = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
            val probe = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(
                Base64.decode(encoded, Base64.DEFAULT),
                0,
                Base64.decode(encoded, Base64.DEFAULT).size,
                probe
            )
            if (probe.outWidth > 0 && probe.outHeight > 0) encoded else clean
        }
    }.getOrDefault(clean)
}

/** 把 SafeInCloud XML 卡片并入密码库，返回 (新增条目数, 新增标签数)。 */
internal fun mergeSafeInCloudCards(vault: Vault, cards: List<SafeInCloudCard>): Pair<Int, Int> {
    val labelsByName = vault.labels.associateBy { it.name }.toMutableMap()
    var newLabels = 0
    cards.forEach { card ->
        val labelIds = card.labelNames.mapNotNull { name ->
            val existing = labelsByName[name]
            val label = if (existing != null) {
                existing
            } else {
                Label(name = name).also {
                    vault.labels.add(it)
                    labelsByName[name] = it
                    newLabels++
                }
            }
            label.id
        }
        val item = card.item
        item.fields = item.fields.map { it.copy(id = newId()) }.toMutableList()
        item.attachments = mutableListOf()
        item.iconData = compactIconData(item.iconData)
        item.labelIds = labelIds.toMutableList()
        vault.items.add(item)
    }
    return cards.size to newLabels
}
