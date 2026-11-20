package com.yaoahos.mysafepass.core.storage

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 云端文件版本（Last-Modified）必须落盘。
 *
 * 它以前只放在 ViewModel 内存里，一冷启动就归零，只能拿服务器时间跟本机
 * `lastSyncedAt` 硬比，服务器稍快就恒判「云端有更新」→ 每次启动整包下载。
 */
class VaultStoreMetaTest {

    @Test
    fun `remote mtime survives reopen`() {
        val root = Files.createTempDirectory("vault-store-test")
        try {
            val store = VaultStore(root)
            val (_, meta) = store.create("测试库", "test-password-123".toCharArray())
            assertEquals("新建库还没有云端版本记录", 0L, meta.lastRemoteMtime)

            store.updateRemoteMtime(meta.id, 1_700_000_123_000L)

            // 重新开一个 store（等价于冷启动后重新读索引），值应当还在。
            val reopened = VaultStore(root).list().first { it.id == meta.id }
            assertEquals(1_700_000_123_000L, reopened.lastRemoteMtime)

            // 0 = 清除（换了服务器或目录）；负数视为没取到值，不该写坏已有记录。
            VaultStore(root).updateRemoteMtime(meta.id, -1)
            assertEquals(
                "负数应被忽略",
                1_700_000_123_000L,
                VaultStore(root).list().first { it.id == meta.id }.lastRemoteMtime
            )
            VaultStore(root).updateRemoteMtime(meta.id, 0)
            assertEquals(
                "0 应清除记录",
                0L,
                VaultStore(root).list().first { it.id == meta.id }.lastRemoteMtime
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
