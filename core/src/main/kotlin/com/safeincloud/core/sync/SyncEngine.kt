package com.safeincloud.core.sync

import com.safeincloud.core.crypto.VaultContainer
import com.safeincloud.core.model.Label
import com.safeincloud.core.model.Vault
import com.safeincloud.core.model.VaultItem
import com.safeincloud.core.model.now
import com.safeincloud.core.storage.AttachmentStore

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
    val attachmentsUploaded: Int = 0,
    val attachmentsDownloaded: Int = 0,
    val message: String = ""
)

class SyncException(message: String, cause: Throwable? = null) : Exception(message, cause)

class SyncEngine(
    private val webDav: WebDavClient,
    private val config: WebDavConfig,
    private val attachmentStore: AttachmentStore,
    vaultId: String,
    vaultName: String = ""
) {
    private val remoteKey: String = config.vaultKey(vaultName.ifBlank { vaultId })

    /**
     * Force the cloud copy to replace the local vault, regardless of timestamps.
     * Used by the WebDAV screen's explicit “拉取云端数据” action.
     */
    suspend fun pullRemote(local: VaultContainer, password: CharArray): SyncResult {
        val remotePath = config.remotePath(remoteKey)
        val remoteBytes = webDav.get(remotePath)
            ?: throw SyncException("云端没有找到密码库文件，无法拉取")
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
        val updated = local.withVault(downloaded)
        val attachments = syncAttachments(downloaded)
        return SyncResult(
            container = updated,
            direction = SyncDirection.DOWNLOADED,
            attachmentsUploaded = attachments.first,
            attachmentsDownloaded = attachments.second,
            message = "已用云端数据覆盖本地"
        )
    }

    suspend fun sync(
        local: VaultContainer,
        password: CharArray,
        conflictStrategy: ConflictStrategy = ConflictStrategy.MERGE
    ): SyncResult {
        val remotePath = config.remotePath(remoteKey)
        val remoteBytes = webDav.get(remotePath)
        if (remoteBytes == null) {
            local.ensureContentKey()
            local.vault.lastSyncedAt = now()
            local.vault.touch()
            webDav.put(remotePath, local.toBytes())
            val attachments = syncAttachments(local.vault)
            return SyncResult(
                container = local,
                direction = SyncDirection.UPLOADED,
                attachmentsUploaded = attachments.first,
                attachmentsDownloaded = attachments.second,
                message = "已上传到 WebDAV"
            )
        }

        val remote = try {
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
            !localChanged && !remoteChanged -> {
                val attachments = syncAttachments(local.vault)
                SyncResult(
                    container = local,
                    direction = SyncDirection.NONE,
                    attachmentsUploaded = attachments.first,
                    attachmentsDownloaded = attachments.second,
                    message = "已是最新状态"
                )
            }

            !localChanged && remoteChanged -> {
                val downloaded = remote.vault.copy(
                    lastSyncedAt = now(),
                    deviceId = local.vault.deviceId
                )
                val updated = local.withVault(downloaded)
                val attachments = syncAttachments(downloaded)
                SyncResult(
                    container = updated,
                    direction = SyncDirection.DOWNLOADED,
                    attachmentsUploaded = attachments.first,
                    attachmentsDownloaded = attachments.second,
                    message = "已下载远端更新"
                )
            }

            localChanged && !remoteChanged -> {
                local.vault.lastSyncedAt = now()
                webDav.put(remotePath, local.toBytes())
                val attachments = syncAttachments(local.vault)
                SyncResult(
                    container = local,
                    direction = SyncDirection.UPLOADED,
                    attachmentsUploaded = attachments.first,
                    attachmentsDownloaded = attachments.second,
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
                    webDav.put(remotePath, local.toBytes())
                    val attachments = syncAttachments(local.vault)
                    return SyncResult(
                        container = local,
                        direction = SyncDirection.UPLOADED,
                        attachmentsUploaded = attachments.first,
                        attachmentsDownloaded = attachments.second,
                        message = "已保留本地版本并覆盖云端"
                    )
                }
                if (conflictStrategy == ConflictStrategy.KEEP_REMOTE) {
                    val downloaded = remote.vault.copy(
                        lastSyncedAt = now(),
                        deviceId = local.vault.deviceId
                    )
                    val updated = local.withVault(downloaded)
                    val attachments = syncAttachments(downloaded)
                    return SyncResult(
                        container = updated,
                        direction = SyncDirection.DOWNLOADED,
                        attachmentsUploaded = attachments.first,
                        attachmentsDownloaded = attachments.second,
                        message = "已使用云端版本覆盖本地"
                    )
                }
                val merge = VaultMerger.merge(local.vault, remote.vault, last)
                val updated = local.withVault(merge.vault)
                webDav.put(remotePath, updated.toBytes())
                val attachments = syncAttachments(merge.vault)
                SyncResult(
                    container = updated,
                    direction = SyncDirection.MERGED,
                    conflicts = merge.conflicts,
                    attachmentsUploaded = attachments.first,
                    attachmentsDownloaded = attachments.second,
                    message = if (merge.conflicts > 0) {
                        "已合并，产生 ${merge.conflicts} 个冲突副本"
                    } else {
                        "已合并本地与远端更新"
                    }
                )
            }
        }
    }

    private suspend fun syncAttachments(vault: Vault): Pair<Int, Int> {
        val attachments = vault.items.flatMap { it.attachments }.distinctBy { it.id }
        if (attachments.isEmpty()) return 0 to 0
        val dir = config.attachmentsDir(remoteKey)
        var uploaded = 0
        var downloaded = 0
        for (ref in attachments) {
            val remotePath = "$dir/${ref.id}.sica"
            val localExists = attachmentStore.exists(ref.id)
            if (!localExists) {
                val remoteBytes = webDav.get(remotePath) ?: continue
                attachmentStore.writeRaw(ref.id, remoteBytes)
                downloaded++
            } else if (!webDav.exists(remotePath)) {
                val bytes = attachmentStore.readRaw(ref.id) ?: continue
                webDav.put(remotePath, bytes)
                uploaded++
            }
        }
        return uploaded to downloaded
    }
}
