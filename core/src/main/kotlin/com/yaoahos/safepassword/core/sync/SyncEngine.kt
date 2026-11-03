package com.yaoahos.safepassword.core.sync

import com.yaoahos.safepassword.core.crypto.VaultContainer
import com.yaoahos.safepassword.core.model.Vault
import com.yaoahos.safepassword.core.model.now

enum class SyncDirection {
    NONE,
    UPLOADED,
    DOWNLOADED,
    MERGED,
    CONFLICT,
    /** 云端已删除这个密码库（另一端删的），本地副本应当跟着删除。 */
    REMOTE_DELETED
}

enum class ConflictStrategy {
    /** Only detect both-side changes and let the UI ask the user. */
    DETECT,
    /** Keep local data and overwrite the cloud copy. */
    KEEP_LOCAL,
    /** Replace local data with the cloud copy. */
    KEEP_REMOTE,
    /** Merge both sides and keep conflict copies. */
    MERGE
}

data class SyncResult(
    /** The caller must persist this container (it may be a new instance after a download/merge). */
    val container: VaultContainer,
    val direction: SyncDirection,
    val conflicts: Int = 0,
    val message: String = ""
)

class SyncException(message: String, cause: Throwable? = null) : Exception(message, cause)

class SyncEngine(
    private val webDav: WebDavClient,
    private val config: WebDavConfig
) {
    /**
     * 云端文件名的键：唯一、终身不变，存在密码库自身随数据同步。
     *
     * 新库在创建时就已经生成好了；只有 1.5.2 之前创建的库还没有键，
     * 这时先按库名去云端清单里认领已存在的文件（那些键是按库名哈希算的，
     * 但一旦认领就写进密码库、以后不再重算），认领不到才生成一个新的随机键。
     */
    private suspend fun resolveKey(local: VaultContainer): String {
        // 键的唯一来源是内容密钥：改名、改主密码都不变，与桌面端 key_for_content 完全一致。
        val fromContent = CloudManifest.keyForContent(local.vault.contentKey)
        if (fromContent != null) {
            if (local.vault.cloudKey != fromContent) local.vault.cloudKey = fromContent
            return fromContent
        }
        // contentKey 缺失（极老的库）才退回存储键 / 清单认领 / 新键。
        val existing = local.vault.cloudKey
        if (existing.isNotBlank()) return existing
        val claimed = runCatching { manifestEntryFor(local.vault.name)?.key }.getOrNull()
        val key = claimed?.takeIf { it.isNotBlank() } ?: CloudManifest.newKey()
        local.vault.cloudKey = key
        return key
    }

    /** 云端清单里显示名与给定名字相同的那一条（大小写、首尾空格不敏感）。 */
    private suspend fun manifestEntryFor(name: String): CloudVaultEntry? {
        val manifest = CloudManifest.parse(
            webDav.get(config.manifestPath())?.toString(Charsets.UTF_8)
        )
        val want = name.trim().lowercase()
        return manifest.vaults.firstOrNull { it.name.trim().lowercase() == want }
    }

    /**
     * Force the cloud copy to replace the local vault, regardless of timestamps.
     * Used by the WebDAV screen's explicit “拉取云端数据” action.
     */
    suspend fun pullRemote(local: VaultContainer, password: CharArray): SyncResult {
        val remoteKey = resolveKey(local)
        val remotePath = config.remotePath(remoteKey)
        val remoteBytes = webDav.get(remotePath)
            ?: CloudManifest.legacyKeyForContent(local.vault.contentKey)?.let { webDav.get(config.remotePath(it)) }
            ?: webDav.get(config.legacyRemotePath())
            ?: throw SyncException("云端没有找到这个密码库的文件，请确认已同步过或检查远程目录")
        val remote = try {
            VaultContainer.open(remoteBytes, password)
        } catch (e: Exception) {
            throw SyncException("无法用当前主密码解密远端数据库，请确认两端使用同一主密码", e)
        }
        if (local.vault.contentKey.isNotBlank() &&
            remote.vault.contentKey.isNotBlank() &&
            local.vault.contentKey != remote.vault.contentKey
        ) {
            throw SyncException("远端数据库与本地数据库不是同一来源（内容密钥不一致），已停止拉取")
        }
        val downloaded = remote.vault.copy(
            lastSyncedAt = now(),
            deviceId = local.vault.deviceId,
            cloudKey = remote.vault.cloudKey.ifBlank { remoteKey }
        )
        return SyncResult(
            container = local.withVault(downloaded),
            direction = SyncDirection.DOWNLOADED,
            message = "已用云端数据覆盖本地"
        )
    }

    /**
     * 把本地这份强制推回云端（「云端已删除」弹窗里的「恢复到云端」）。
     * 不看时间戳、不判断云端有没有同名文件，直接写本体并维护清单。
     */
    suspend fun restoreToCloud(local: VaultContainer): SyncResult {
        val remoteKey = resolveKey(local)
        local.ensureContentKey()
        local.vault.touch()
        local.vault.lastSyncedAt = local.vault.updatedAt
        upload(config.remotePath(remoteKey), local, remoteKey)
        return SyncResult(
            container = local,
            direction = SyncDirection.UPLOADED,
            message = "已恢复到云端"
        )
    }

    suspend fun sync(
        local: VaultContainer,
        password: CharArray,
        conflictStrategy: ConflictStrategy = ConflictStrategy.MERGE
    ): SyncResult {
        var remoteKey = resolveKey(local)
        var remotePath = config.remotePath(remoteKey)
        var remoteBytes = webDav.get(remotePath)
        var adoptedLegacy: VaultContainer? = null
        if (remoteBytes == null) {
            // 认领已有的云端文件：只有内容密钥一致（同一密码库）才认领，
            // 否则视为云端还没有本库文件，直接上传本地，避免读到别的密码库的数据。
            // 依次尝试：根目录单文件 → 按库名派生的键。
            // 旧布局：单文件 SafePassword.spv，以及按库名哈希命名的老文件（清单里还记着键）。
            val claimedKey = runCatching { manifestEntryFor(local.vault.name)?.key }.getOrNull()
            val candidates = listOfNotNull(
                config.legacyRemotePath(),
                claimedKey?.takeIf { it.isNotBlank() }?.let { config.remotePath(it) },
                CloudManifest.legacyKeyForContent(local.vault.contentKey)?.let { config.remotePath(it) }
            ).distinct().filter { it != remotePath }
            for (candidate in candidates) {
                val bytes = webDav.get(candidate) ?: continue
                val container = runCatching { VaultContainer.open(bytes, password) }.getOrNull()
                if (container != null && container.vault.contentKey.isNotBlank() &&
                    container.vault.contentKey == local.vault.contentKey
                ) {
                    remoteBytes = bytes
                    adoptedLegacy = container
                    break
                }
            }
        }

        // 云端既没有本库文件、也没有可认领的旧文件时判为「被删了」。
        // 与桌面端同一套规则：不看墓碑，只看「曾同步过 + 文件确实没了」，
        // 先按库名去清单里认领改名后的新键，仍找不到才交给界面询问怎么处理。
        if (remoteBytes == null && local.vault.lastSyncedAt > 0) {
            val manifest = CloudManifest.parse(
                webDav.get(config.manifestPath())?.toString(Charsets.UTF_8)
            )
            val want = local.vault.name.trim().lowercase()
            val entry = manifest.vaults.firstOrNull { it.name.trim().lowercase() == want }
            if (entry != null && entry.key != remoteKey) {
                remoteKey = entry.key
                remotePath = config.remotePath(remoteKey)
                remoteBytes = webDav.get(remotePath)
            }
            if (remoteBytes == null) {
                // 本地还有没同步出去的改动时，提示里要说清楚，因为删本地会丢数据。
                val pending = local.vault.updatedAt > local.vault.lastSyncedAt + 3_000
                return SyncResult(
                    container = local,
                    direction = SyncDirection.REMOTE_DELETED,
                    message = if (pending) {
                        "云端已删除该密码库（本地有未同步的修改）"
                    } else {
                        "云端已删除该密码库"
                    }
                )
            }
        }
        if (remoteBytes == null) {
            local.ensureContentKey()
            local.vault.lastSyncedAt = now()
            local.vault.touch()
            upload(remotePath, local, remoteKey)
            return SyncResult(
                container = local,
                direction = SyncDirection.UPLOADED,
                message = "已上传到 WebDAV"
            )
        }

        val remote = adoptedLegacy ?: try {
            VaultContainer.open(remoteBytes, password)
        } catch (e: Exception) {
            throw SyncException("无法用当前主密码解密远端数据库，请确认两端使用同一主密码", e)
        }

        if (local.vault.contentKey.isNotBlank() &&
            remote.vault.contentKey.isNotBlank() &&
            local.vault.contentKey != remote.vault.contentKey
        ) {
            throw SyncException("远端数据库与本地数据库不是同一来源（内容密钥不一致），已停止同步")
        }

        val last = local.vault.lastSyncedAt
        // 两台设备的墙上时钟不可比：差几秒就会把「对端有改动」误判成「没改动」，
        // 从而拿本地旧数据覆盖云端（实测：手机新增条目后，平板解锁反而覆盖了云端，
        // 直到手机再改一次才同步过去）。所以默认路径不按时间戳挑赢家，一律合并。
        val localChanged = local.vault.updatedAt > last + CLOCK_SKEW_MS
        val remoteChanged = remote.vault.updatedAt > last + CLOCK_SKEW_MS

        if (conflictStrategy == ConflictStrategy.DETECT && localChanged && remoteChanged) {
            return SyncResult(
                container = local,
                direction = SyncDirection.CONFLICT,
                conflicts = 1,
                message = "检测到本地与云端都有新修改，请选择处理方式"
            )
        }
        if (conflictStrategy == ConflictStrategy.KEEP_LOCAL && localChanged) {
            local.vault.lastSyncedAt = now()
            upload(remotePath, local, remoteKey)
            return SyncResult(
                container = local,
                direction = SyncDirection.UPLOADED,
                message = "已保留本地版本并覆盖云端"
            )
        }
        if (conflictStrategy == ConflictStrategy.KEEP_REMOTE && remoteChanged) {
            val adopted = remote.vault.copy(
                lastSyncedAt = now(),
                deviceId = local.vault.deviceId,
                cloudKey = remote.vault.cloudKey.ifBlank { remoteKey }
            )
            return SyncResult(
                container = local.withVault(adopted),
                direction = SyncDirection.DOWNLOADED,
                message = "已使用云端版本覆盖本地"
            )
        }

        val merge = VaultMerger.merge(local.vault, remote.vault, last)
        val mergedVault = merge.vault.copy(
            name = if (remote.vault.updatedAt > local.vault.updatedAt) remote.vault.name else local.vault.name,
            cloudKey = local.vault.cloudKey.ifBlank { remote.vault.cloudKey.ifBlank { remoteKey } },
            deviceId = local.vault.deviceId
        )

        if (fingerprint(mergedVault) == fingerprint(remote.vault)) {
            // 云端已经包含全部改动（常见于只有对端动过），本地换成合并结果就够了。
            val adopted = mergedVault.copy(lastSyncedAt = now())
            return SyncResult(
                container = local.withVault(adopted),
                direction = if (remoteChanged) SyncDirection.DOWNLOADED else SyncDirection.NONE,
                conflicts = merge.conflicts,
                message = when {
                    merge.conflicts > 0 -> "已合并，产生 ${merge.conflicts} 个冲突副本"
                    remoteChanged -> "已下载远端更新"
                    else -> "已是最新状态"
                }
            )
        }

        val updated = mergedVault.copy(lastSyncedAt = now())
        upload(remotePath, local.withVault(updated), remoteKey)
        return SyncResult(
            container = local.withVault(updated),
            direction = SyncDirection.MERGED,
            conflicts = merge.conflicts,
            message = when {
                merge.conflicts > 0 -> "已合并，产生 ${merge.conflicts} 个冲突副本"
                remoteChanged -> "已合并本地与远端更新"
                else -> "已上传本地更新"
            }
        )
    }

    /**
     * 内容指纹：只看会同步的字段，且与列表顺序无关。
     * 用来判断「合并结果是否已经在云端」，避免每次同步都白传一遍整库。
     */
    private fun fingerprint(vault: Vault): String {
        val items = vault.items.sortedBy { it.id }
            .joinToString("|") { "${it.id}:${it.updatedAt}:${it.status}:${it.title}" }
        val labels = vault.labels.sortedBy { it.id }
            .joinToString("|") { "${it.id}:${it.name}:${it.color}:${it.sortOrder}" }
        val tombstones = vault.tombstones.sortedBy { it.id }
            .joinToString("|") { "${it.id}:${it.deletedAt}" }
        return "$items#$labels#$tombstones"
    }

    /**
     * 上传密码库本体，并顺带维护云端清单。
     *
     * 清单失败不应让整次同步失败——清单只服务「从云端恢复」时的选择列表，
     * 真读不到时恢复流程还能列目录兜底。
     */
    private suspend fun upload(remotePath: String, container: VaultContainer, remoteKey: String) {
        val bytes = container.toBytes()
        webDav.put(remotePath, bytes)
        runCatching {
            val manifest = CloudManifest.parse(
                webDav.get(config.manifestPath())?.toString(Charsets.UTF_8)
            )
            // 同一个库因改名/换键可能留下旧条目，按名字清掉，恢复列表里才不会出现两条。
            val name = container.vault.name.trim().lowercase()
            manifest.vaults.removeAll { it.key != remoteKey && it.name.trim().lowercase() == name }
            manifest.upsert(remoteKey, container.vault.name, container.vault.updatedAt, bytes.size.toLong())
            webDav.put(config.manifestPath(), manifest.toJson().toByteArray(Charsets.UTF_8))
        }
    }
    private companion object {
        /** 允许的时钟偏差：小于这个差值就当作「同一时刻」。 */
        const val CLOCK_SKEW_MS = 3_000L
    }}
