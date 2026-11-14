package com.yaoahos.safepassword.core.sync

import com.yaoahos.safepassword.core.crypto.VaultContainer
import com.yaoahos.safepassword.core.model.Vault
import com.yaoahos.safepassword.core.model.VaultItem
import com.yaoahos.safepassword.core.model.now
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 同步判定的回归测试。
 *
 * 背景：两台设备的墙上时钟并不可比。曾经按 `updatedAt` 与 `lastSyncedAt` 比大小来决定
 * 「谁更新」，结果手机新增条目后，平板因为本机时钟更快，认为云端没变化，于是：
 * 要么永远不下载那条新条目，要么反而用自己的旧数据覆盖云端。
 */
class SyncEngineTest {

    private val password = "test-password-123".toCharArray()

    private class FakeDav {
        val files = mutableMapOf<String, ByteArray>()

        /** 每次 PUT 成功后的钩子，用来模拟「别的设备在本次同步进行中写入云端」。 */
        var afterPut: ((String) -> Unit)? = null

        fun engine() = MockEngine { request ->
            val path = request.url.encodedPath.trimStart('/')
            when (request.method) {
                HttpMethod.Get ->
                    files[path]?.let { respond(it, HttpStatusCode.OK) }
                        ?: respond("", HttpStatusCode.NotFound)

                HttpMethod.Head ->
                    if (files.containsKey(path)) respond("", HttpStatusCode.OK)
                    else respond("", HttpStatusCode.NotFound)

                HttpMethod.Put -> {
                    val body = request.body
                    files[path] = if (body is OutgoingContent.ByteArrayContent) body.bytes() else ByteArray(0)
                    afterPut?.invoke(path)
                    respond("", HttpStatusCode.Created)
                }

                HttpMethod("MKCOL") -> respond("", HttpStatusCode.Created)
                HttpMethod.Delete -> {
                    files.remove(path)
                    respond("", HttpStatusCode.NoContent)
                }

                else -> respond("", HttpStatusCode.OK)
            }
        }
    }

    private fun item(id: String, updatedAt: Long) =
        VaultItem(id = id, title = "条目 $id", createdAt = updatedAt, updatedAt = updatedAt)

    /** 两端同一个库：内容密钥一致，只是各自的数据不同。 */
    private fun vaultWith(
        contentKey: String,
        updatedAt: Long,
        lastSyncedAt: Long,
        items: List<VaultItem>
    ) = Vault().apply {
        name = "测试库"
        this.contentKey = contentKey
        this.updatedAt = updatedAt
        this.lastSyncedAt = lastSyncedAt
        this.items.addAll(items)
    }

    @Test
    fun `remote-only item is merged even when remote timestamp looks older`() = runBlocking {
        val t = now()
        val contentKey = VaultContainer.create(Vault(), password).vault.contentKey

        // 云端：有 A、B 两条，但时间戳是「过去」（对端时钟偏慢）。
        val remoteVault = vaultWith(contentKey, t - 5_000, t - 5_000, listOf(item("a", t - 9_000), item("b", t - 4_000)))
        val fake = FakeDav()
        val config = WebDavConfig(baseUrl = "https://dav.example.com/")

        // 本地：只有 A，且刚同步过（lastSyncedAt 比云端时间戳晚）。
        val localVault = vaultWith(contentKey, t, t, listOf(item("a", t - 9_000)))
        localVault.cloudKey = "ktest"
        val local = VaultContainer.create(localVault, password)
        fake.files[config.remotePath(requireNotNull(CloudManifest.keyForContent(contentKey)))] =
            VaultContainer.create(remoteVault, password).toBytes()

        val webDav = WebDavClient(config, fake.engine())
        try {
            val result = SyncEngine(webDav, config).sync(local, password)
            val titles = result.container.vault.items.map { it.id }.sorted()
            assertEquals(listOf("a", "b"), titles)
        } finally {
            webDav.close()
        }
    }

    @Test
    fun `local-only item is kept and pushed to cloud`() = runBlocking {
        val t = now()
        val contentKey = VaultContainer.create(Vault(), password).vault.contentKey

        val remoteVault = vaultWith(contentKey, t - 5_000, t - 5_000, listOf(item("a", t - 9_000)))
        val localVault = vaultWith(contentKey, t, t, listOf(item("a", t - 9_000), item("c", t - 1_000)))
        localVault.cloudKey = "ktest"

        val fake = FakeDav()
        val config = WebDavConfig(baseUrl = "https://dav.example.com/")
        val key = config.remotePath(requireNotNull(CloudManifest.keyForContent(contentKey)))
        fake.files[key] = VaultContainer.create(remoteVault, password).toBytes()

        val webDav = WebDavClient(config, fake.engine())
        try {
            val result = SyncEngine(webDav, config).sync(VaultContainer.create(localVault, password), password)
            assertEquals(listOf("a", "c"), result.container.vault.items.map { it.id }.sorted())

            val pushed = VaultContainer.open(fake.files.getValue(key), password)
            assertEquals(listOf("a", "c"), pushed.vault.items.map { it.id }.sorted())
            assertTrue("本地新增的条目必须已经传到云端", pushed.vault.items.any { it.id == "c" })
        } finally {
            webDav.close()
        }
    }

    @Test
    fun `same name vault in cloud is reported as name conflict, never as deleted`() = runBlocking {
        val t = now()
        val localKey = VaultContainer.create(Vault(), password).vault.contentKey
        val otherKey = VaultContainer.create(Vault().apply { name = "测试库" }, password).vault.contentKey

        // 本地这个库「同步过」但云端没有它的文件；云端另有一个同名、不同内容密钥的库。
        val localVault = vaultWith(localKey, t, t - 10_000, listOf(item("a", t - 9_000)))
        localVault.cloudKey = requireNotNull(CloudManifest.keyForContent(localKey))
        val otherVault = vaultWith(otherKey, t - 5_000, t - 5_000, listOf(item("z", t - 6_000)))

        val fake = FakeDav()
        val config = WebDavConfig(baseUrl = "https://dav.example.com/")
        val otherKeyPath = config.remotePath(requireNotNull(CloudManifest.keyForContent(otherKey)))
        fake.files[otherKeyPath] = VaultContainer.create(otherVault, password).toBytes()
        val otherEntryKey = requireNotNull(CloudManifest.keyForContent(otherKey))
        fake.files[config.manifestPath()] = CloudManifest().apply {
            upsert(otherEntryKey, "测试库", t - 5_000, 1)
        }.toJson().toByteArray(Charsets.UTF_8)

        val webDav = WebDavClient(config, fake.engine())
        try {
            val engine = SyncEngine(webDav, config)
            val first = engine.sync(VaultContainer.create(localVault, password), password)
            assertEquals(SyncDirection.NAME_CONFLICT, first.direction)

            // 关掉弹窗后再同步一次：仍应是同名冲突，
            // 不能因为上一次尝试动过 lastSyncedAt 就误报「云端已删除」。
            val second = engine.sync(VaultContainer.create(localVault, password), password)
            assertEquals(SyncDirection.NAME_CONFLICT, second.direction)

            // 另一个库的云端文件必须原封不动。
            val other = VaultContainer.open(fake.files.getValue(otherKeyPath), password)
            assertEquals(listOf("z"), other.vault.items.map { it.id })
        } finally {
            webDav.close()
        }
    }

    /**
     * 云端清单是所有库共用的一份文件。本机从「读清单」到「写清单」之间隔着一次整包上传，
     * 别的设备完全可能在这期间登记了自己的库。写回时必须重新读最新清单、只 upsert 自己这条，
     * 否则那台设备的条目会被旧快照覆盖掉——云端列表少一个库，文件还在但用户看不到。
     */
    @Test
    fun `manifest keeps entries that another device added during this upload`() = runBlocking {
        val t = now()
        val contentKey = VaultContainer.create(Vault(), password).vault.contentKey
        // lastSyncedAt = 0：首次上传（lastSyncedAt > 0 且云端无文件会先判「云端已删除」）。
        val localVault = vaultWith(contentKey, t, 0, listOf(item("a", t)))
        localVault.cloudKey = requireNotNull(CloudManifest.keyForContent(contentKey))

        val config = WebDavConfig(baseUrl = "https://dav.example.com/")
        val fake = FakeDav()
        val preexistingKey = "k00000000000000aa"
        val intruderKey = "k00000000000000bb"

        // 云端已经登记了另一个库
        fake.files[config.manifestPath()] = CloudManifest().apply {
            upsert(preexistingKey, "已有的库", t - 9_000, 111)
        }.toJson().toByteArray(Charsets.UTF_8)

        // 库文件刚写完的那一刻，模拟另一台设备登记了它自己的库
        var injected = false
        fake.afterPut = { path ->
            if (!injected && path.endsWith(".spv")) {
                injected = true
                val current = CloudManifest.parse(
                    fake.files.getValue(config.manifestPath()).toString(Charsets.UTF_8)
                )
                current.upsert(intruderKey, "另一台设备的库", t, 222)
                fake.files[config.manifestPath()] =
                    current.toJson().toByteArray(Charsets.UTF_8)
            }
        }

        val webDav = WebDavClient(config, fake.engine())
        try {
            SyncEngine(webDav, config).sync(VaultContainer.create(localVault, password), password)

            val finalManifest = CloudManifest.parse(
                fake.files.getValue(config.manifestPath()).toString(Charsets.UTF_8)
            )
            val keys = finalManifest.vaults.map { it.key }.toSet()
            assertTrue("原本就在的库条目不能被抹掉", keys.contains(preexistingKey))
            assertTrue("上传期间别的设备登记的库必须保留", keys.contains(intruderKey))
            assertTrue(
                "本库自己也要登记上",
                keys.contains(requireNotNull(CloudManifest.keyForContent(contentKey)))
            )
        } finally {
            webDav.close()
        }
    }
}
