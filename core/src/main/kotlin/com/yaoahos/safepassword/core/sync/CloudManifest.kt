package com.yaoahos.safepassword.core.sync

import com.yaoahos.safepassword.core.json.AppJson
import kotlinx.serialization.Serializable
import java.security.MessageDigest

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
         * 密码库名 → 稳定 ASCII 键。
         * 只用名字做输入，保证同一密码库在任何设备上都算出同一个键（本地 id 导入后会变，不能用）。
         * 两名同库的情况由「库名唯一校验」在创建时挡住。
         */
        fun keyFor(name: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(name.trim().toByteArray(Charsets.UTF_8))
            return "v" + digest.joinToString("") { "%02x".format(it) }.take(16)
        }
    }
}
