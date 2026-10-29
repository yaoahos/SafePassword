package com.safeincloud.core

import com.safeincloud.core.crypto.VaultContainer
import com.safeincloud.core.crypto.Crypto
import com.safeincloud.core.crypto.WrongPasswordException
import com.safeincloud.core.model.Field
import com.safeincloud.core.model.FieldType
import com.safeincloud.core.model.Vault
import com.safeincloud.core.model.VaultItem
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
        val dir = Files.createTempDirectory("sicv-test")
        val file = dir.resolve("vault.sicv")
        val container = VaultContainer.create(Vault(name = "文件库"), "password-123".toCharArray())
        container.saveTo(file)
        val reopened = VaultContainer.open(file, "password-123".toCharArray())
        assertEquals("文件库", reopened.vault.name)
    }
}
