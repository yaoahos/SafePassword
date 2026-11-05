package com.yaoahos.safepassword.core.sync

import com.yaoahos.safepassword.core.json.AppJson
import kotlinx.serialization.Serializable

/**
 * 云端密码库清单。
 *
 * 云端文件名必须是纯 ASCII（有些网盘对中文文件名支持很差），所以文件名里不放库名。
 * 清单把「传输用的 key」映射回「人看的名字」，同时带更新时间和大小，供恢复时选择。
 *
 * 删除不做墓碑标记：桌面端（SafePasswordDesktop `sp/cloud.rs`）的清单只有 `vaults`，
 * 写回时会把多出来的字段丢掉。判定「云端已删除」统一按
 * 「曾同步过（lastSyncedAt > 0）+ 云端文件确实不存在」来做，两端规则一致才不会互相打架。
 */
@Serializable
data class CloudVaultEntry(
    val key: String,
    var name: String,
    var updatedAt: Long = 0,
    var size: Long = 0
)

@Serializable
data class CloudManifest(
    var vaults: MutableList<CloudVaultEntry> = mutableListOf()
) {
    fun upsert(key: String, name: String, updatedAt: Long, size: Long) {
        val position = vaults.indexOfFirst { it.key == key }
        val entry = CloudVaultEntry(key, name, updatedAt, size)
        if (position >= 0) vaults[position] = entry else vaults.add(entry)
    }

    /** 移除指定条目（云端密码库删除 / 改名后清理旧键）。 */
    fun remove(key: String) {
        vaults.removeAll { it.key == key }
    }

    fun toJson(): String = AppJson.instance.encodeToString(serializer(), this)

    companion object {
        fun parse(text: String?): CloudManifest {
            if (text.isNullOrBlank()) return CloudManifest()
            return runCatching {
                AppJson.instance.decodeFromString(serializer(), text)
            }.getOrDefault(CloudManifest())
        }

        /**
         * 密码库内容密钥 → 云端键。
         *
         * 与桌面端 `key_for_content` 一致：`k` + contentKey 前 8 字节的小写 hex（共 17 字符）。
         * contentKey 是建库时随机生成的 32 字节，改名、改主密码都不会变，
         * 所以它就是这个密码库的身份——键因此唯一且终身不变。
         * 只取前 8 字节是为了文件名短一些（64 位随机，实际不会撞）。
         */
        fun keyForContent(contentKey: String): String? {
            val trimmed = contentKey.trim()
            if (trimmed.isEmpty()) return null
            val bytes = runCatching {
                java.util.Base64.getDecoder().decode(trimmed)
            }.getOrNull() ?: return null
            if (bytes.isEmpty()) return null
            return "k" + bytes.take(8).joinToString("") { "%02x".format(it) }
        }

        /**
         * 上一版用完整 contentKey 生成的键（`k` + 64 位 hex）。
         * 只用于升级时认领旧文件，认领后按新键重新上传。
         */
        fun legacyKeyForContent(contentKey: String): String? {
            val trimmed = contentKey.trim()
            if (trimmed.isEmpty()) return null
            val bytes = runCatching {
                java.util.Base64.getDecoder().decode(trimmed)
            }.getOrNull() ?: return null
            if (bytes.isEmpty()) return null
            return "k" + bytes.joinToString("") { "%02x".format(it) }
        }

        /** 生成一个新的云端键（contentKey 缺失时的兜底，正常路径用不到）。 */
        fun newKey(): String {
            val bytes = ByteArray(8)
            java.security.SecureRandom().nextBytes(bytes)
            return "v" + bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
