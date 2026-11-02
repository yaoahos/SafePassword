package com.yaoahos.safepassword.core.sync

import com.yaoahos.safepassword.core.crypto.VaultContainer
import com.yaoahos.safepassword.core.model.Vault
import com.yaoahos.safepassword.core.model.now

enum class SyncDirection {
    NONE,
    UPLOADED,
    DOWNLOADED,
    MERGED,
    CONFLICT
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
    private val config: WebDavConfig,
    vaultName: String
) {
    /** 云端文件名里的 ASCII 键；只由库名决定，跨设备稳定，且不含中文。 */
    private val remoteKey: String = CloudManifest.keyFor(vaultName)

    /**
     * Force the cloud copy to replace the local vault, regardless of timestamps.
     * Used by the WebDAV screen's explicit “拉取云端数据” action.
     */
    suspend fun pullRemote(local: VaultContainer, password: CharArray): SyncResult {
        val remotePath = config.remotePath(remoteKey)
        val remoteBytes = webDav.get(remotePath)
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
            deviceId = local.vault.deviceId
        )
        return SyncResult(
            container = local.withVault(downloaded),
            direction = SyncDirection.DOWNLOADED,
            message = "已用云端数据覆盖本地"
        )
    }

    suspend fun sync(
        local: VaultContainer,
        password: CharArray,
        conflictStrategy: ConflictStrategy = ConflictStrategy.MERGE
    ): SyncResult {
        val remotePath = config.remotePath(remoteKey)
        var remoteBytes = webDav.get(remotePath)
        var adoptedLegacy: VaultContainer? = null
        if (remoteBytes == null) {
            // 升级迁移：旧版文件只有内容密钥一致（同一密码库）才认领，
            // 否则视为云端还没有本库文件，直接上传本地，避免读到别的密码库的数据。
            // 依次尝试：旧版单文件 SafePassword.spv → 1.4.1 的按库名键。
            val candidates = listOf(
                config.legacyRemotePath(),
                config.legacyNamePath(local.vault.name)
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
        if (remoteBytes == null) {
            local.ensureContentKey()
            local.vault.lastSyncedAt = now()
            local.vault.touch()
            upload(remotePath, local)
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
        val localChanged = local.vault.updatedAt > last
        val remoteChanged = remote.vault.updatedAt > last

        return when {
            !localChanged && !remoteChanged -> SyncResult(
                container = local,
                direction = SyncDirection.NONE,
                message = "已是最新状态"
            )

            !localChanged && remoteChanged -> {
                val downloaded = remote.vault.copy(
                    lastSyncedAt = now(),
                    deviceId = local.vault.deviceId
                )
                SyncResult(
                    container = local.withVault(downloaded),
                    direction = SyncDirection.DOWNLOADED,
                    message = "已下载远端更新"
                )
            }

            localChanged && !remoteChanged -> {
                local.vault.lastSyncedAt = now()
                upload(remotePath, local)
                SyncResult(
                    container = local,
                    direction = SyncDirection.UPLOADED,
                    message = "已上传本地更新"
                )
            }

            else -> {
                if (conflictStrategy == ConflictStrategy.DETECT) {
                    return SyncResult(
                        container = local,
                        direction = SyncDirection.CONFLICT,
                        conflicts = 1,
                        message = "检测到本地与云端都有新修改，请选择处理方式"
                    )
                }
                if (conflictStrategy == ConflictStrategy.KEEP_LOCAL) {
                    local.vault.lastSyncedAt = now()
                    upload(remotePath, local)
                    return SyncResult(
                        container = local,
                        direction = SyncDirection.UPLOADED,
                        message = "已保留本地版本并覆盖云端"
                    )
                }
                if (conflictStrategy == ConflictStrategy.KEEP_REMOTE) {
                    val downloaded = remote.vault.copy(
                        lastSyncedAt = now(),
                        deviceId = local.vault.deviceId
                    )
                    return SyncResult(
                        container = local.withVault(downloaded),
                        direction = SyncDirection.DOWNLOADED,
                        message = "已使用云端版本覆盖本地"
                    )
                }
                val merge = VaultMerger.merge(local.vault, remote.vault, last)
                val updated = local.withVault(merge.vault)
                upload(remotePath, updated)
                SyncResult(
                    container = updated,
                    direction = SyncDirection.MERGED,
                    conflicts = merge.conflicts,
                    message = if (merge.conflicts > 0) {
                        "已合并，产生 ${merge.conflicts} 个冲突副本"
                    } else {
                        "已合并本地与远端更新"
                    }
                )
            }
        }
    }

    /**
     * 上传密码库本体，并顺带维护云端清单。
     *
     * 清单失败不应让整次同步失败——清单只服务「从云端恢复」时的选择列表，
     * 真读不到时恢复流程还能列目录兜底。
     */
    private suspend fun upload(remotePath: String, container: VaultContainer) {
        val bytes = container.toBytes()
        webDav.put(remotePath, bytes)
        runCatching {
            val manifest = CloudManifest.parse(
                webDav.get(config.manifestPath())?.toString(Charsets.UTF_8)
            )
            manifest.upsert(remoteKey, container.vault.name, container.vault.updatedAt, bytes.size.toLong())
            webDav.put(config.manifestPath(), manifest.toJson().toByteArray(Charsets.UTF_8))
        }
    }
}
