package com.yaoahos.safepassword.core.sync

import com.yaoahos.safepassword.core.json.AppJson
import kotlinx.serialization.Serializable
import java.security.MessageDigest

/**
 * 云端密码库清单。
 *
 * 云端文件名必须是纯 ASCII（有些网盘对中文文件名支持很差），所以文件名里不放库名。
 * 清单把「传输用的 key」映射回「人看的名字」，同时带更新时间和大小，供恢复时选择。
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
    var vaults: MutableList<CloudVaultEntry> = mutableListOf(),
    /**
     * 已删除密码库的墓碑。
     *
     * 只删文件不够：另一台设备还留着本地副本，下次同步发现「云端没有」就会
     * 重新上传，两边立刻分叉。墓碑记下删除时间，其它设备据此同步删除本地副本。
     */
    var deleted: MutableList<CloudVaultEntry> = mutableListOf()
) {
    fun upsert(key: String, name: String, updatedAt: Long, size: Long) {
        val position = vaults.indexOfFirst { it.key == key }
        val entry = CloudVaultEntry(key, name, updatedAt, size)
        if (position >= 0) vaults[position] = entry else vaults.add(entry)
        deleted.removeAll { it.key == key }
    }

    fun remove(key: String) {
        vaults.removeAll { it.key == key }
        deleted.removeAll { it.key == key }
    }

    /** 记一条删除墓碑，供其它设备识别「这个库是被主动删掉的」。 */
    fun markDeleted(key: String, name: String, at: Long) {
        vaults.removeAll { it.key == key }
        deleted.removeAll { it.key == key }
        deleted.add(CloudVaultEntry(key, name, at, 0))
    }

    /** 该键被删除的时间；没有墓碑返回 0。 */
    fun deletedAt(key: String): Long = deleted.firstOrNull { it.key == key }?.updatedAt ?: 0

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
