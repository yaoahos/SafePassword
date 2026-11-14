package com.yaoahos.safepassword.core.sync

import com.yaoahos.safepassword.core.model.AttachmentRef
import com.yaoahos.safepassword.core.model.Tombstone
import com.yaoahos.safepassword.core.model.Vault
import com.yaoahos.safepassword.core.model.VaultItem
import com.yaoahos.safepassword.core.model.now
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

    // ---- 冲突判定：两端都改过才算冲突，且判定依据必须是「内容不同」而不是时间戳 ----

    @Test
    fun `both sides edited creates a conflict copy`() {
        val local = Vault().apply { items.add(item("a", updatedAt = t, title = "手机改的")) }
        val remote = Vault().apply { items.add(item("a", updatedAt = t - 1_000, title = "电脑改的")) }

        val result = VaultMerger.merge(local, remote, lastSyncedAt = t - 5_000)

        assertEquals(1, result.conflicts)
        assertEquals(2, result.vault.items.size)
        assertEquals("手机改的", result.vault.items[0].title)
        assertTrue(
            "败者应保留为冲突副本",
            result.vault.items[1].title.startsWith("电脑改的")
        )
    }

    @Test
    fun `same timestamp but different content still conflicts`() {
        // 同毫秒编辑（或批量导入）会让两端撞出同一个 updatedAt。
        // 此时内容不同，必须判冲突——曾因 `updatedAt !=` 前置条件而静默取本地、丢掉远端改动。
        val local = Vault().apply { items.add(item("a", updatedAt = t, title = "手机改的")) }
        val remote = Vault().apply { items.add(item("a", updatedAt = t, title = "电脑改的")) }

        val result = VaultMerger.merge(local, remote, lastSyncedAt = t - 5_000)

        assertEquals("时间戳相同但内容不同必须产生冲突副本", 1, result.conflicts)
        assertEquals(2, result.vault.items.size)
    }

    @Test
    fun `same timestamp and same content produces no conflict`() {
        val local = Vault().apply { items.add(item("a", updatedAt = t)) }
        val remote = Vault().apply { items.add(item("a", updatedAt = t)) }

        val result = VaultMerger.merge(local, remote, lastSyncedAt = t - 5_000)

        assertEquals(0, result.conflicts)
        assertEquals(1, result.vault.items.size)
    }

    @Test
    fun `attachment-only change still conflicts`() {
        // 附件不参与过 sameContent 的比较时，Android 会把「只有附件变了」判成没变，
        // 直接取时间戳大的那侧，另一端的附件改动被静默丢弃。桌面端一直比 attachments。
        val withAttachment = item("a", updatedAt = t, title = "同标题").apply {
            attachments.add(AttachmentRef(id = "att-1", name = "a.pdf", size = 1_024))
        }
        val local = Vault().apply { items.add(withAttachment) }
        val remote = Vault().apply { items.add(item("a", updatedAt = t - 1_000, title = "同标题")) }

        val result = VaultMerger.merge(local, remote, lastSyncedAt = t - 5_000)

        assertEquals("只有附件不同的两端必须判冲突", 1, result.conflicts)
        assertEquals(2, result.vault.items.size)
    }
}
