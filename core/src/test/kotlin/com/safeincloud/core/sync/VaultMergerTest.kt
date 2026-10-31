package com.safeincloud.core.sync

import com.safeincloud.core.model.Tombstone
import com.safeincloud.core.model.Vault
import com.safeincloud.core.model.VaultItem
import com.safeincloud.core.model.now
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 覆盖“永久删除的条目被云端复活”这一回归问题。
 * 合并规则集中在 [VaultMerger]，这里直接对它做纯函数测试，不需要网络与附件目录。
 */
class VaultMergerTest {

    private val t: Long = now()

    private fun item(id: String, updatedAt: Long, title: String = "条目 $id") =
        VaultItem(id = id, title = title, createdAt = updatedAt, updatedAt = updatedAt)

    @Test
    fun `permanently deleted item is not resurrected from remote`() {
        val local = Vault().apply { tombstones.add(Tombstone("a", deletedAt = t)) }
        val remote = Vault().apply { items.add(item("a", updatedAt = t - 500)) }

        val merged = VaultMerger.merge(local, remote, lastSyncedAt = t - 2_000).vault

        assertTrue("被删除的条目不应从云端复活", merged.items.none { it.id == "a" })
        assertTrue("墓碑应被保留并继续传播", merged.tombstones.any { it.id == "a" })
    }

    @Test
    fun `remote tombstone also deletes the local copy`() {
        val local = Vault().apply { items.add(item("a", updatedAt = t - 500)) }
        val remote = Vault().apply { tombstones.add(Tombstone("a", deletedAt = t)) }

        val merged = VaultMerger.merge(local, remote, lastSyncedAt = t - 2_000).vault

        assertTrue("另一台设备上的删除同样应生效", merged.items.none { it.id == "a" })
    }

    @Test
    fun `new remote item is still merged in`() {
        val local = Vault()
        val remote = Vault().apply { items.add(item("a", updatedAt = t)) }

        val merged = VaultMerger.merge(local, remote, lastSyncedAt = t - 2_000).vault

        assertEquals(1, merged.items.size)
        assertEquals("a", merged.items.first().id)
    }

    @Test
    fun `editing after deletion wins and clears the tombstone`() {
        val local = Vault().apply { tombstones.add(Tombstone("a", deletedAt = t - 1_000)) }
        val remote = Vault().apply { items.add(item("a", updatedAt = t)) }

        val merged = VaultMerger.merge(local, remote, lastSyncedAt = t - 2_000).vault

        assertTrue("删除之后又被编辑过，应以编辑为准", merged.items.any { it.id == "a" })
        assertFalse("条目已恢复，墓碑应被撤销", merged.tombstones.any { it.id == "a" })
    }

    @Test
    fun `tombstones are unioned keeping the latest deletion time`() {
        val local = Vault().apply { tombstones.add(Tombstone("a", deletedAt = t - 2_000)) }
        val remote = Vault().apply { tombstones.add(Tombstone("a", deletedAt = t - 1_000)) }

        val merged = VaultMerger.merge(local, remote, lastSyncedAt = t - 3_000).vault

        val tombstones = merged.tombstones.filter { it.id == "a" }
        assertEquals(1, tombstones.size)
        assertEquals(t - 1_000, tombstones.first().deletedAt)
    }

    @Test
    fun `expired tombstones are purged`() {
        val local = Vault().apply {
            tombstones.add(Tombstone("old", deletedAt = t - Vault.TOMBSTONE_TTL_MS - 5_000))
        }

        val merged = VaultMerger.merge(local, Vault(), lastSyncedAt = t - 10_000).vault

        assertFalse("超过保留期的墓碑应被清理", merged.tombstones.any { it.id == "old" })
    }

    @Test
    fun `tombstone does not affect other items`() {
        val local = Vault().apply { tombstones.add(Tombstone("a", deletedAt = t)) }
        val remote = Vault().apply {
            items.add(item("a", updatedAt = t - 500))
            items.add(item("b", updatedAt = t - 500))
        }

        val merged = VaultMerger.merge(local, remote, lastSyncedAt = t - 2_000).vault

        assertEquals(listOf("b"), merged.items.map { it.id })
    }
}
