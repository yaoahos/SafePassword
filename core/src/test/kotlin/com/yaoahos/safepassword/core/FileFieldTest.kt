package com.yaoahos.safepassword.core

import com.yaoahos.safepassword.core.io.VaultCodec
import com.yaoahos.safepassword.core.io.VaultXml
import com.yaoahos.safepassword.core.json.AppJson
import com.yaoahos.safepassword.core.model.Field
import com.yaoahos.safepassword.core.model.FieldType
import com.yaoahos.safepassword.core.model.MAX_FIELD_FILE_BYTES
import com.yaoahos.safepassword.core.model.Vault
import com.yaoahos.safepassword.core.model.VaultItem
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 文件字段（FieldType.FILE）与明文 JSON 逐字节口径的回归。
 * 序列化规则：value 存 base64，fileName / fileMime 空值不写进 JSON，
 * 与桌面端 `sp/model.rs` 的 skip_serializing_if 保持一致。
 */
class FileFieldTest {

    private fun fileField(
        content: ByteArray = "hello".toByteArray(),
        name: String = "hi.txt",
        mime: String = "text/plain"
    ): Field = Field(
        label = "附件",
        type = FieldType.FILE,
        value = java.util.Base64.getEncoder().encodeToString(content),
        fileName = name,
        fileMime = mime
    )

    @Test
    fun `file field round trips through json with metadata`() {
        val field = fileField()
        val json = AppJson.instance.encodeToString(Field.serializer(), field)
        val back = AppJson.instance.decodeFromString(Field.serializer(), json)
        assertEquals("hi.txt", back.fileName)
        assertEquals("text/plain", back.fileMime)
        assertArrayEquals("hello".toByteArray(), back.fileBytes())
    }

    @Test
    fun `empty file metadata is omitted from json`() {
        val field = Field(label = "账号", value = "u")
        val json = AppJson.instance.encodeToString(Field.serializer(), field)
        assertFalse("空 fileName 不应写出：$json", json.contains("fileName"))
        assertFalse("空 fileMime 不应写出：$json", json.contains("fileMime"))
        assertFalse("空 history 不应写出：$json", json.contains("history"))
    }

    @Test
    fun `file bytes decode only for file type`() {
        assertNull(Field(label = "x", value = "aGVsbG8=").fileBytes())
        assertNull(Field(label = "x", type = FieldType.FILE, value = "").fileBytes())
        assertArrayEquals("hello".toByteArray(), fileField().fileBytes())
    }

    @Test
    fun `max file size constant is 50kb`() {
        assertEquals(50 * 1024, MAX_FIELD_FILE_BYTES)
    }

    @Test
    fun `xml export writes file name placeholder not content`() {
        val vault = Vault(name = "库")
        vault.items.add(VaultItem(title = "t", fields = mutableListOf(fileField())))
        val xml = VaultXml.export(vault)
        assertTrue("XML 应含文件名占位", xml.contains("hi.txt"))
        assertFalse("XML 不应含 base64 内容", xml.contains("aGVsbG8="))
    }

    @Test
    fun `csv export ignores file fields in username lookup`() {
        val vault = Vault(name = "库")
        vault.items.add(
            VaultItem(
                title = "t",
                fields = mutableListOf(
                    fileField(name = "账号.txt"),
                    Field(label = "用户名", value = "alice", type = FieldType.TEXT)
                )
            ).also { it.fields[0] = it.fields[0].copy(label = "用户名") }
        )
        val csv = VaultCodec.toCsv(vault)
        assertTrue("CSV 用户名应取文本字段", csv.contains("alice"))
        assertFalse("CSV 不应泄漏 base64", csv.contains("aGVsbG8="))
    }

    @Test
    fun `golden fixture re-serializes byte identical`() {
        // 与桌面端黄金样本同源：重排/缺省键都会改变明文 JSON 的 SHA-256，两端必须逐字节一致。
        val json = requireNotNull(javaClass.getResourceAsStream("/interop/known1.vault.json"))
            .use { it.readBytes() }
            .toString(Charsets.UTF_8)
        val vault = AppJson.instance.decodeFromString(Vault.serializer(), json)
        val out = AppJson.instance.encodeToString(Vault.serializer(), vault)
        assertEquals("明文 JSON 重序列化必须逐字节一致", json, out)
    }
}
