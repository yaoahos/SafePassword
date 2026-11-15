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
 * 同步空转回归：两端内容一致时不该再上传，本地真改了则必须上传。
 *
 * 用桌面端产出的跨端样本 `interop/known1.vault.json`（含一个 2023 年的墓碑）——
 * 那个墓碑超过 90 天保留期，曾让「两端完全相同」被判成「本地有改动」并整库重传。
 */
class SyncIdleTest {

    private val password = "test-password-123".toCharArray()

    private class FakeDav {
        val files = mutableMapOf<String, ByteArray>()
        var puts = 0

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
                    puts++
                    val body = request.body
                    files[path] = if (body is OutgoingContent.ByteArrayContent) body.bytes() else ByteArray(0)
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

    @Test
    fun `sync after reopen does not upload again`() = runBlocking {
        val now = 1_700_000_000_000L
        val vault = Vault().apply {
            name = "测试库"
            createdAt = now
            updatedAt = now
            items.add(VaultItem(id = "a", title = "条目 a", createdAt = now, updatedAt = now))
        }
        val dav = FakeDav()
        val config = WebDavConfig(baseUrl = "https://dav.example.com/")
        val webDav = WebDavClient(config, dav.engine())
        try {
            val first = SyncEngine(webDav, config).sync(VaultContainer.create(vault, password), password)
            println("[diag] first  = ${first.direction} / ${first.message} / puts=${dav.puts}")

            val reopened = VaultContainer.open(first.container.toBytes(), password)
            println("[diag] reopened lastSyncedAt=${reopened.vault.lastSyncedAt} updatedAt=${reopened.vault.updatedAt} cloudKey=${reopened.vault.cloudKey}")

            val second = SyncEngine(webDav, config).sync(reopened, password)
            println("[diag] second = ${second.direction} / ${second.message} / puts=${dav.puts}")

            val reopened2 = VaultContainer.open(second.container.toBytes(), password)
            val third = SyncEngine(webDav, config).sync(reopened2, password)
            println("[diag] third  = ${third.direction} / ${third.message} / puts=${dav.puts}")

            assertEquals("第二次同步不该再上传", SyncDirection.NONE, second.direction)
            assertEquals("第三次同步不该再上传", SyncDirection.NONE, third.direction)
        } finally {
            webDav.close()
        }
    }

    @Test
    fun `same content without expired tombstone stays idle`() = runBlocking {
        val samplePassword = "测试密码123!🚀".toCharArray()
        val json = javaClass.getResource("/interop/known1.vault.json")!!.readText(Charsets.UTF_8)
        val vault = com.yaoahos.safepassword.core.json.AppJson.instance
            .decodeFromString(Vault.serializer(), json)
        vault.tombstones.clear()
        val key = CloudManifest.keyForContent(vault.contentKey)!!
        vault.cloudKey = key

        val dav = FakeDav()
        val config = WebDavConfig(baseUrl = "https://dav.example.com/")
        val webDav = WebDavClient(config, dav.engine())
        try {
            dav.files[config.remotePath(key)] = VaultContainer.create(vault.copy(), samplePassword).toBytes()
            val result = SyncEngine(webDav, config).sync(VaultContainer.create(vault.copy(), samplePassword), samplePassword)
            println("[diag] no-tombstone = ${result.direction} / ${result.message} / puts=${dav.puts}")
            assertEquals("没有过期墓碑时不该上传", SyncDirection.NONE, result.direction)
        } finally {
            webDav.close()
        }
    }

    @Test
    fun `cross-end sample syncs idle when both sides are identical`() = runBlocking {
        val samplePassword = "测试密码123!🚀".toCharArray()
        val json = javaClass.getResource("/interop/known1.vault.json")!!.readText(Charsets.UTF_8)
        val vault = com.yaoahos.safepassword.core.json.AppJson.instance
            .decodeFromString(Vault.serializer(), json)
        val contentKey = vault.contentKey
        val key = CloudManifest.keyForContent(contentKey)!!
        vault.cloudKey = key

        val dav = FakeDav()
        val config = WebDavConfig(baseUrl = "https://dav.example.com/")
        val webDav = WebDavClient(config, dav.engine())
        try {
            dav.files[config.remotePath(key)] = VaultContainer.create(vault.copy(), samplePassword).toBytes()

            val first = SyncEngine(webDav, config).sync(VaultContainer.create(vault.copy(), samplePassword), samplePassword)
            println("[diag] sample first  = ${first.direction} / ${first.message} / puts=${dav.puts}")

            val reopened = VaultContainer.open(first.container.toBytes(), samplePassword)
            val second = SyncEngine(webDav, config).sync(reopened, samplePassword)
            println("[diag] sample second = ${second.direction} / ${second.message} / puts=${dav.puts}")

            assertEquals("同一份内容不该上传", SyncDirection.NONE, first.direction)
            assertEquals("同一份内容不该上传", SyncDirection.NONE, second.direction)
        } finally {
            webDav.close()
        }
    }

    @Test
    fun `local edit still uploads even with an expired tombstone`() = runBlocking {
        val samplePassword = "测试密码123!🚀".toCharArray()
        val json = javaClass.getResource("/interop/known1.vault.json")!!.readText(Charsets.UTF_8)
        val vault = com.yaoahos.safepassword.core.json.AppJson.instance
            .decodeFromString(Vault.serializer(), json)
        vault.cloudKey = CloudManifest.keyForContent(vault.contentKey)!!

        val dav = FakeDav()
        val config = WebDavConfig(baseUrl = "https://dav.example.com/")
        val webDav = WebDavClient(config, dav.engine())
        try {
            dav.files[config.remotePath(vault.cloudKey)] =
                VaultContainer.create(vault.copy(), samplePassword).toBytes()
            val putsBefore = dav.puts

            // 本地真的加了一个条目：归一化过期墓碑不能把这种改动一并吞掉。
            val edited = vault.copy()
            edited.items.add(
                VaultItem(id = "new-1", title = "新条目", createdAt = now(), updatedAt = now())
            )
            edited.touch()
            val result = SyncEngine(webDav, config)
                .sync(VaultContainer.create(edited, samplePassword), samplePassword)
            println("[diag] edited = ${result.direction} / ${result.message}")
            assertEquals("本地有新条目应上传", SyncDirection.UPLOADED, result.direction)
            assertTrue("应当真的写了云端", dav.puts > putsBefore)
        } finally {
            webDav.close()
        }
    }
}
