package com.yaoahos.safepassword.core.sync

import com.yaoahos.safepassword.core.crypto.VaultContainer
import com.yaoahos.safepassword.core.json.AppJson
import com.yaoahos.safepassword.core.model.Vault
import com.yaoahos.safepassword.core.model.now
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import java.security.MessageDigest

enum class SyncDirection {
    NONE,
    UPLOADED,
    DOWNLOADED,
    MERGED,
    CONFLICT,
    /** 云端已删除这个密码库（另一端删的），本地副本应当跟着删除。 */
    REMOTE_DELETED,

    /** 云端已有同名但不同内容的密码库：本次不上传，应当提示用户改名后再同步。 */
    NAME_CONFLICT
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
        // contentKey 缺失（极老的库）才退回存储键或新键。
        // 不再「按库名去清单里认领」：云端可能有别的同名库，认错了就会把对端的文件
        // 当成自己的，随后解密出一份内容密钥不同的库并报「不是同一来源」。
        val existing = local.vault.cloudKey
        if (existing.isNotBlank()) return existing
        val key = CloudManifest.newKey()
        local.vault.cloudKey = key
        return key
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
        detectNameConflict(local, remoteKey)?.let { return nameConflict(local, it) }
        local.ensureContentKey()
        local.vault.touch()
        local.vault.lastSyncedAt = local.vault.updatedAt
        upload(config.remotePath(remoteKey), local, remoteKey)?.let { return nameConflict(local, it) }
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
            // 只尝试旧布局：根目录单文件，以及旧版 64 位内容密钥命名的文件。
            // 不按库名去清单里找——云端可能有别的同名库，认错就会报「不是同一来源」。
            val candidates = listOfNotNull(
                config.legacyRemotePath(),
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
        // 与桌面端同一套规则：不看墓碑，只看「曾同步过 + 文件确实没了」。
        if (remoteBytes == null && local.vault.lastSyncedAt > 0) {
            // 兼容旧版误判留下的状态：本库「同步过」但文件不见了，
            // 如果清单里有同名不同键的库，那其实是同名冲突而不是被删，
            // 先按同名冲突提示用户改名，别让人以为云端把库删了。
            detectNameConflict(local, remoteKey)?.let { return nameConflict(local, it) }
            val pending = local.vault.updatedAt > local.vault.lastSyncedAt + CLOCK_SKEW_MS
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
        if (remoteBytes == null) {
            // 同名检查必须放在改动本地状态之前。
            // 下面会把 lastSyncedAt 推到 now()、touch() 抬高 updatedAt，
            // 一旦上传被同名挡住又不回退，下一次同步就会以为「本库同步过、文件却没了」，
            // 从而误报「云端已删除该密码库」。
            detectNameConflict(local, remoteKey)?.let { return nameConflict(local, it) }
            local.ensureContentKey()
            local.vault.lastSyncedAt = now()
            local.vault.touch()
            upload(remotePath, local, remoteKey)?.let { return nameConflict(local, it) }
            return SyncResult(
                container = local,
                direction = SyncDirection.UPLOADED,
                message = "已上传本地更新"
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
            detectNameConflict(local, remoteKey)?.let { return nameConflict(local, it) }
            local.vault.lastSyncedAt = now()
            upload(remotePath, local, remoteKey)?.let { return nameConflict(local, it) }
            return SyncResult(
                container = local,
                direction = SyncDirection.UPLOADED,
                // 传就是传，不因为用了哪种策略换措辞：同一件事换个说法只会让人以为出了两种问题。
                message = "已上传本地更新"
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
            // 库名跟着「谁更晚改过库」走，且必须晚于上次同步——否则远端那份没动过的
            // 旧名字会把本地刚改的新名字压回去。与桌面端 sync.rs 的判定保持一致。
            name = if (remote.vault.updatedAt > local.vault.updatedAt &&
                remote.vault.updatedAt > last
            ) {
                remote.vault.name
            } else {
                local.vault.name
            },
            cloudKey = local.vault.cloudKey.ifBlank { remote.vault.cloudKey.ifBlank { remoteKey } },
            deviceId = local.vault.deviceId
        )

        // 方向判断一律基于内容指纹，不再比较 updatedAt：
        // 两端墙上时钟不可比，「远端是否变过」用时间戳永远只能猜。
        val localFp = fingerprint(local.vault)
        val remoteFp = fingerprint(remote.vault)
        val mergedFp = fingerprint(mergedVault)
        // 合并结果比本地多出内容 → 这次确实从云端拿到了东西。
        val downloaded = mergedFp != localFp

        if (mergedFp == remoteFp) {
            // 云端已经包含全部改动（常见于只有对端动过），本地换成合并结果就够了。
            val adopted = mergedVault.copy(lastSyncedAt = now())
            return SyncResult(
                container = local.withVault(adopted),
                direction = if (downloaded) SyncDirection.DOWNLOADED else SyncDirection.NONE,
                conflicts = merge.conflicts,
                message = when {
                    merge.conflicts > 0 -> "已合并，产生 ${merge.conflicts} 个冲突副本"
                    downloaded -> "已下载远端更新"
                    else -> "已是最新状态"
                }
            )
        }

        val updated = mergedVault.copy(lastSyncedAt = now())
        detectNameConflict(local, remoteKey)?.let { return nameConflict(local, it) }
        upload(remotePath, local.withVault(updated), remoteKey)
            ?.let { return nameConflict(local.withVault(updated), it) }
        return SyncResult(
            container = local.withVault(updated),
            direction = if (downloaded) SyncDirection.MERGED else SyncDirection.UPLOADED,
            conflicts = merge.conflicts,
            message = when {
                merge.conflicts > 0 -> "已合并，产生 ${merge.conflicts} 个冲突副本"
                downloaded -> "已合并本地与远端更新"
                else -> "已上传本地更新"
            }
        )
    }

    /**
     * 只读地检查「云端是否已有同名但不同键的库」。
     *
     * 上传前先查一次，是为了在改动本地状态之前就能干净地退出：
     * 一旦上传被同名挡住，本地的 lastSyncedAt/updatedAt 不能被改过。
     */
    private suspend fun detectNameConflict(local: VaultContainer, remoteKey: String): String? {
        val text = try {
            webDav.get(config.manifestPath())?.toString(Charsets.UTF_8)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 读不到清单就不判冲突，交给 upload() 里的同一套逻辑处理。
            return null
        }
        val manifest = CloudManifest.parse(text)
        val want = local.vault.name.trim().lowercase()
        return manifest.vaults
            .firstOrNull { it.key != remoteKey && it.name.trim().lowercase() == want }
            ?.name
    }

    /**
     * 内容指纹：只抹掉「每次同步都会变、但用户不可见」的簿记字段，其余全量参与。
     *
     * 曾经只拼 `id:updatedAt:status:title`，字段值、备注、网址、标签关联、附件都不参与——
     * 「updatedAt 相同但内容不同」会被误判成「云端已包含全部改动」，改动从此再也传不上去。
     * 现在与桌面端 `content_hash` 同语义（两端各自计算、不跨端比较），并在序列化前按 id 排序，
     * 保证列表顺序不同不算内容变化。
     */
    private fun fingerprint(vault: Vault): String {
        val canonical = vault.copy(
            id = "",
            updatedAt = 0,
            lastSyncedAt = 0,
            revision = 0,
            deviceId = "",
            items = vault.items.sortedBy { it.id }.toMutableList(),
            labels = vault.labels.sortedBy { it.id }.toMutableList(),
            tombstones = vault.tombstones.sortedBy { it.id }.toMutableList()
        )
        val bytes = AppJson.instance
            .encodeToString(Vault.serializer(), canonical)
            .toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * 上传密码库本体，并顺带维护云端清单。
     *
     * 写库之前先读清单：
     * - 云端已有**同名但不同 key** 的密码库时返回那个名字，本次不上传，
     *   交给界面提示改名——否则云端会出现两个同名库，用户根本分不清。
     * - 读清单**失败**时只上传库文件、跳过清单重写。绝不能把「读失败」当成
     *   空清单写回去，否则会把别的密码库条目全清掉（新建一个库 → 云端只剩它）。
     *
     * @return 冲突的另一个库名；没有冲突返回 null。
     */
    private suspend fun upload(remotePath: String, container: VaultContainer, remoteKey: String): String? {
        val manifestText = try {
            webDav.get(config.manifestPath())?.toString(Charsets.UTF_8)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 读不到清单：只传本体，清单等下次能读到再补。
            webDav.put(remotePath, container.toBytes())
            return null
        }

        val manifest = CloudManifest.parse(manifestText)
        val want = container.vault.name.trim().lowercase()
        manifest.vaults
            .firstOrNull { it.key != remoteKey && it.name.trim().lowercase() == want }
            ?.let { return it.name }

        val bytes = container.toBytes()
        webDav.put(remotePath, bytes)
        // 清单是所有密码库共用的一份文件。上面读它的时间点到此刻已经隔着一次整包上传，
        // 期间别的设备很可能刚写过清单；直接拿旧快照写回会把别人新加的条目抹掉
        // （云端列表里少一个库，文件还在但用户看不到）。
        // 所以写入前再取一次最新清单，只把自己这条 upsert 进去。
        val latestText = try {
            webDav.get(config.manifestPath())?.toString(Charsets.UTF_8)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 取不到就用进来时读到的那份，至少保证本库登记不丢。
            null
        }
        val base = CloudManifest.parse(latestText ?: manifestText)
        base.upsert(remoteKey, container.vault.name, container.vault.updatedAt, bytes.size.toLong())
        webDav.put(config.manifestPath(), base.toJson().toByteArray(Charsets.UTF_8))
        return null
    }

    private fun nameConflict(local: VaultContainer, other: String) = SyncResult(
        container = local,
        direction = SyncDirection.NAME_CONFLICT,
        message = "云端已有同名密码库「$other」，请改名后再同步"
    )
    private companion object {
        /** 允许的时钟偏差：小于这个差值就当作「同一时刻」。 */
        const val CLOCK_SKEW_MS = 3_000L
    }}
