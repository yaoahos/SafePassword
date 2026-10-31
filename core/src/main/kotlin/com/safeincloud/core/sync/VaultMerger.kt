package com.safeincloud.core.sync

import com.safeincloud.core.model.Label
import com.safeincloud.core.model.Tombstone
import com.safeincloud.core.model.Vault
import com.safeincloud.core.model.VaultItem
import com.safeincloud.core.model.now

/**
 * 纯函数合并：本地库 + 远端库 -> 合并结果。
 *
 * 从 [SyncEngine] 里独立出来，一是便于单元测试（不需要网络与附件目录），
 * 二是让“删除不复活”的规则集中在一处，避免以后改动同步流程时被绕过。
 */
internal object VaultMerger {

    data class Result(val vault: Vault, val conflicts: Int)

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
                    if (localNew && remoteNew && localItem.updatedAt != remoteItem.updatedAt &&
                        !sameContent(localItem, remoteItem)
                    ) {
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
        result.purgeOldTombstones()
        return Result(result, conflicts)
    }

    private fun sameContent(a: VaultItem, b: VaultItem): Boolean =
        a.title == b.title &&
            a.fields == b.fields &&
            a.notes == b.notes &&
            a.url == b.url &&
            a.labelIds == b.labelIds &&
            a.status == b.status &&
            a.attachments == b.attachments
}
