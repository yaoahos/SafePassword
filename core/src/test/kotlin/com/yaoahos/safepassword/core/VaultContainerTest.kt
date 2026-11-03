package com.yaoahos.safepassword.core

import com.yaoahos.safepassword.core.crypto.VaultContainer
import com.yaoahos.safepassword.core.crypto.Crypto
import com.yaoahos.safepassword.core.crypto.WrongPasswordException
import com.yaoahos.safepassword.core.model.Field
import com.yaoahos.safepassword.core.model.FieldType
import com.yaoahos.safepassword.core.model.Vault
import com.yaoahos.safepassword.core.model.VaultItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import javax.crypto.spec.SecretKeySpec

class VaultContainerTest {
    @Test
    fun `empty payload round trip`() {
        val key = SecretKeySpec(Crypto.randomBytes(32), "AES")
        val encrypted = Crypto.encrypt(key, ByteArray(0))
        assertTrue(Crypto.decrypt(key, encrypted).isEmpty())
    }
    @Test
    fun `round trip encrypts and decrypts`() {
        val vault = Vault(name = "测试库")
        vault.items.add(
            VaultItem(
                title = "示例",
                fields = mutableListOf(Field(label = "密码", value = "s3cret", type = FieldType.PASSWORD))
            )
        )
        val password = "correct horse battery staple".toCharArray()
        val container = VaultContainer.create(vault, password)
        val bytes = container.toBytes()

        val reopened = VaultContainer.open(bytes, password)
        assertEquals("测试库", reopened.vault.name)
        assertEquals("s3cret", reopened.vault.items.first().primaryPassword)
        assertTrue(reopened.vault.contentKey.isNotBlank())
    }

    @Test(expected = WrongPasswordException::class)
    fun `wrong password fails`() {
        val container = VaultContainer.create(Vault(), "password-123".toCharArray())
        VaultContainer.open(container.toBytes(), "password-456".toCharArray())
    }

    @Test
    fun `change password keeps content key and items`() {
        val vault = Vault(name = "库")
        val container = VaultContainer.create(vault, "old-password".toCharArray())
        val contentKey = container.contentKey.encoded.copyOf()
        val changed = container.changePassword("new-password".toCharArray())
        val reopened = VaultContainer.open(changed.toBytes(), "new-password".toCharArray())
        assertEquals("库", reopened.vault.name)
        assertTrue(contentKey.contentEquals(reopened.contentKey.encoded))
        assertNotEquals("old-password", "new-password")
    }

    @Test
    fun `file save and open`() {
        val dir = Files.createTempDirectory("spv-test")
        val file = dir.resolve("vault.spv")
        val container = VaultContainer.create(Vault(name = "文件库"), "password-123".toCharArray())
        container.saveTo(file)
        val reopened = VaultContainer.open(file, "password-123".toCharArray())
        assertEquals("文件库", reopened.vault.name)
    }

    // ---------------------------------------------------------------- 容器 v2

    private fun headerVersion(bytes: ByteArray): Int =
        ((bytes[4].toInt() and 0xFF) shl 24) or
            ((bytes[5].toInt() and 0xFF) shl 16) or
            ((bytes[6].toInt() and 0xFF) shl 8) or
            (bytes[7].toInt() and 0xFF)

    private fun resource(name: String): ByteArray =
        requireNotNull(javaClass.getResourceAsStream("/interop/$name")) { "缺少测试资源 $name" }
            .use { it.readBytes() }

    @Test
    fun `writes version 2 and round trips`() {
        val vault = Vault(name = "测试库")
        vault.items.add(VaultItem(title = "示例", fields = mutableListOf(Field(label = "密码", value = "s3cret", type = FieldType.PASSWORD))))
        val password = "correct horse battery staple".toCharArray()
        val bytes = VaultContainer.create(vault, password).toBytes()

        assertEquals("新写出的容器版本应为 2", 2, headerVersion(bytes))
        val reopened = VaultContainer.open(bytes, password)
        assertEquals("测试库", reopened.vault.name)
        assertEquals("s3cret", reopened.vault.items.first().primaryPassword)
    }

    @Test
    fun `gzip actually shrinks the payload`() {
        val vault = Vault(name = "压缩测试")
        repeat(300) { index ->
            vault.items.add(
                VaultItem(
                    title = "站点 $index",
                    fields = mutableListOf(
                        Field(label = "账号", value = "user$index@example.com"),
                        Field(label = "密码", value = "Pw-$index-9f3a2b7c", type = FieldType.PASSWORD),
                        Field(label = "网址", value = "https://example.com/$index")
                    )
                )
            )
        }
        val json = com.yaoahos.safepassword.core.json.AppJson.instance
            .encodeToString(Vault.serializer(), vault)
            .toByteArray(Charsets.UTF_8)
        val packed = VaultContainer.create(vault, "password-123".toCharArray()).toBytes()
        assertTrue("压缩后应至少小一半：json=${json.size} container=${packed.size}", packed.size * 2 < json.size)
    }

    @Test
    fun `reads version 1 fixture`() {
        val password = resource("known1.password.txt").toString(Charsets.UTF_8).trim().toCharArray()
        val bytes = resource("known1-v1.spv")
        assertEquals(1, headerVersion(bytes))
        val vault = VaultContainer.open(bytes, password).vault
        assertEquals("测试密码库🚀", vault.name)
        assertEquals(5, vault.items.size)
    }

    @Test
    fun `reads version 2 fixture produced by desktop`() {
        val password = resource("known1.password.txt").toString(Charsets.UTF_8).trim().toCharArray()
        val bytes = resource("known1-v2.spv")
        assertEquals(2, headerVersion(bytes))
        val vault = VaultContainer.open(bytes, password).vault

        val expected = com.yaoahos.safepassword.core.json.AppJson.instance
            .decodeFromString(Vault.serializer(), resource("known1.vault.json").toString(Charsets.UTF_8))
        assertEquals(expected.name, vault.name)
        assertEquals(expected.items.size, vault.items.size)
        assertEquals(expected.labels.size, vault.labels.size)
        // 逐条比对标题与字段，确认 gzip 解压后再解析的内容没有偏移。
        expected.items.forEachIndexed { index, item ->
            val actual = vault.items[index]
            assertEquals(item.title, actual.title)
            assertEquals(item.fields.map { it.label to it.value }, actual.fields.map { it.label to it.value })
        }
    }

    @Test
    fun `upgrading a version 1 vault rewrites it as version 2`() {
        val password = resource("known1.password.txt").toString(Charsets.UTF_8).trim().toCharArray()
        val opened = VaultContainer.open(resource("known1-v1.spv"), password)
        val rewritten = opened.toBytes()
        assertEquals(2, headerVersion(rewritten))
        val reopened = VaultContainer.open(rewritten, password)
        assertEquals(opened.vault.items.size, reopened.vault.items.size)
    }

    @Test
    fun `rejects unknown container version`() {
        val bytes = VaultContainer.create(Vault(name = "库"), "password-123".toCharArray()).toBytes()
        bytes[7] = 3
        val failure = runCatching { VaultContainer.open(bytes, "password-123".toCharArray()) }.exceptionOrNull()
        assertTrue(failure is com.yaoahos.safepassword.core.crypto.VaultFormatException)
        assertTrue(failure!!.message!!.contains("不支持的数据库版本：3"))
    }
}
