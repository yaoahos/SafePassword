package com.yaoahos.mysafepass.core.sync

import com.yaoahos.mysafepass.core.model.Label
import com.yaoahos.mysafepass.core.model.Tombstone
import com.yaoahos.mysafepass.core.model.Vault
import com.yaoahos.mysafepass.core.model.VaultItem
import com.yaoahos.mysafepass.core.model.now

/**
 * 纯函数合并：本地库 + 远端库 -> 合并结果。
 *
 * 从 [SyncEngine] 里独立出来，一是便于单元测试（不需要网络与附件目录），
 * 二是让“删除不复活”的规则集中在一处，避免以后改动同步流程时被绕过。
 */
internal object VaultMerger {

    data class Result(val vault: Vault, val conflicts: Int)

    /**
     * 抹掉已过保留期的墓碑，返回归一化后的库。
     *
     * 墓碑过期后既不能复活条目、也拦不住任何同步，纯粹是簿记残留；可它是库内容的一部分，
     * 于是「哪一端先把它清掉」就成了内容差异。实测：云端只剩一个 2023 年的过期墓碑、
     * 两端条目完全相同，[merge] 末尾的清理让合并结果与云端对不上，同步被判成
     * 「本地有改动」并整库上传——每次冷启动都白白传一遍。
     *
     * 所以同步前先把两端都归一化（见 [SyncEngine.sync]），三个内容指纹都基于归一化结果。
     */
    fun dropExpiredTombstones(vault: Vault, before: Long = now() - Vault.TOMBSTONE_TTL_MS): Vault {
        if (vault.tombstones.none { it.deletedAt < before }) return vault
        return vault.copy(
            tombstones = vault.tombstones.filter { it.deletedAt >= before }.toMutableList()
        )
    }

    fun merge(local: Vault, remote: Vault, lastSyncedAt: Long): Result {
        val localMap = local.items.associateBy { it.id }
        val remoteMap = remote.items.associateBy { it.id }

        // 墓碑取并集，同一个 id 保留更晚的删除时间。
        val tombstones = LinkedHashMap<String, Tombstone>()
        for (tombstone in local.tombstones + remote.tombstones) {
            val existing = tombstones[tombstone.id]
            if (existing == null || tombstone.deletedAt > existing.deletedAt) {
                tombstones[tombstone.id] = tombstone
            }
        }

        val merged = mutableListOf<VaultItem>()
        var conflicts = 0

        for (id in localMap.keys + remoteMap.keys) {
            val localItem = localMap[id]
            val remoteItem = remoteMap[id]
            val tombstone = tombstones[id]

            if (tombstone != null) {
                // 两侧都没有比删除更新的修改时，保持删除，绝不从云端复活。
                val localGone = localItem == null || tombstone.deletedAt >= localItem.updatedAt
                val remoteGone = remoteItem == null || tombstone.deletedAt >= remoteItem.updatedAt
                if (localGone && remoteGone) {
                    continue
                }
                // 删除之后另一端又编辑过，以这次编辑为准，撤销墓碑。
                tombstones.remove(id)
            }

            when {
                localItem == null && remoteItem != null -> merged.add(remoteItem)
                remoteItem == null && localItem != null -> merged.add(localItem)
                localItem != null && remoteItem != null -> {
                    val localNew = localItem.updatedAt > lastSyncedAt
                    val remoteNew = remoteItem.updatedAt > lastSyncedAt
                    // 判冲突只看「两边都改过 + 内容确实不同」。不能再加
                    // `updatedAt !=` 这类条件：时间戳相同不代表是同一份改动
                    // （同毫秒编辑、批量导入都可能在两端撞出同一个时间戳），
                    // 一旦漏判冲突就会静默丢掉一端的内容。
                    if (localNew && remoteNew && !sameContent(localItem, remoteItem)) {
                        val winner = if (localItem.updatedAt >= remoteItem.updatedAt) localItem else remoteItem
                        val loser = if (winner === localItem) remoteItem else localItem
                        merged.add(winner)
                        merged.add(loser.copyAsConflictCopy())
                        conflicts++
                    } else if (remoteItem.updatedAt > localItem.updatedAt) {
                        merged.add(remoteItem)
                    } else {
                        merged.add(localItem)
                    }
                }
            }
        }

        val labels = LinkedHashMap<String, Label>()
        local.labels.forEach { labels[it.id] = it }
        remote.labels.forEach { labels.putIfAbsent(it.id, it) }

        val result = local.copy(
            items = merged.toMutableList(),
            labels = labels.values.toMutableList(),
            tombstones = tombstones.values.toMutableList(),
            updatedAt = now(),
            lastSyncedAt = now(),
            revision = maxOf(local.revision, remote.revision) + 1
        )
        // 顺带清掉过期墓碑。同步路径传进来的两端已经归一化（[dropExpiredTombstones]），
        // 这里对它们是无操作；留着是为了让直接调用 merge 的场景（测试、导入）也不会越清越乱。
        result.purgeOldTombstones()
        return Result(result, conflicts)
    }

    /**
     * 内容是否一致。
     *
     * 必须与桌面端 `sp/merger.rs` 的 `same_content` 逐项对齐，否则同一份数据在
     * 两端会得出不同的「算不算冲突」结论——Android 少比一项就会把「只有附件变了」
     * 判成没变，直接取时间戳大的那侧，另一端的附件改动被静默丢掉。
     */
    private fun sameContent(a: VaultItem, b: VaultItem): Boolean =
        a.title == b.title &&
            a.fields == b.fields &&
            a.notes == b.notes &&
            a.url == b.url &&
            a.labelIds == b.labelIds &&
            a.status == b.status &&
            a.attachments == b.attachments
}
