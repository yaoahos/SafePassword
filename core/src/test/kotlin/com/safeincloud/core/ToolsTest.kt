package com.safeincloud.core

import com.safeincloud.core.io.SafeInCloudXml
import com.safeincloud.core.io.VaultCodec
import com.safeincloud.core.model.Field
import com.safeincloud.core.model.FieldType
import com.safeincloud.core.model.TemplateCatalog
import com.safeincloud.core.model.Vault
import com.safeincloud.core.model.VaultItem
import com.safeincloud.core.security.GeneratorOptions
import com.safeincloud.core.security.PasswordGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolsTest {
    @Test
    fun `generator respects length and character sets`() {
        val password = PasswordGenerator.generate(
            GeneratorOptions(length = 32, lowercase = true, uppercase = true, digits = true, symbols = true)
        )
        assertEquals(32, password.length)
        assertTrue(password.any { it in 'a'..'z' })
        assertTrue(password.any { it in 'A'..'Z' })
        assertTrue(password.any { it in '0'..'9' })
        assertTrue(password.any { !it.isLetterOrDigit() })
    }

    @Test
    fun `csv round trip`() {
        val vault = Vault(name = "CSV")
        vault.items.add(
            VaultItem(
                templateId = TemplateCatalog.LOGIN,
                title = "GitHub",
                fields = mutableListOf(
                    Field(label = "用户名", value = "alice"),
                    Field(label = "密码", value = "p,a\"ss", type = FieldType.PASSWORD)
                ),
                notes = "备注",
                url = "https://github.com"
            )
        )
        val csv = VaultCodec.toCsv(vault)
        val imported = VaultCodec.itemsFromCsv(csv)
        assertEquals(1, imported.size)
        assertEquals("GitHub", imported.first().title)
        assertEquals("alice", imported.first().primaryUsername)
        assertEquals("p,a\"ss", imported.first().primaryPassword)
    }

    @Test
    fun `safeincloud xml import keeps fields labels favorite and skips templates`() {
        val xml = """
            <database>
              <label id="1" name="工作"/>
              <card id="c1" title="GitHub" star="true" template="false">
                <notes>个人账号</notes>
                <field name="login" type="login">alice</field>
                <field name="password" type="password">s3cret</field>
                <field name="website" type="url">https://github.com</field>
                <field name="Email" type="login">alice@example.com</field>
                <field name="What was the name of First Car?" type="password">Ford</field>
                <field name="备注" type="notes">工作用途</field>
                <image>data:image/png;base64,
                  iVBORw0KGgo=
                </image>
                <label_id>1</label_id>
              </card>
              <card id="t1" title="模板" template="true"/>
            </database>
        """.trimIndent()
        val cards = SafeInCloudXml.parse(xml)
        assertEquals(1, cards.size)
        val card = cards.first()
        assertEquals("GitHub", card.item.title)
        assertEquals("alice", card.item.primaryUsername)
        assertEquals("s3cret", card.item.primaryPassword)
        assertEquals("https://github.com", card.item.url)
        assertTrue(card.item.favorite)
        assertEquals(listOf("工作"), card.labelNames)
        assertTrue(card.item.fields.any { it.label == "邮箱" && it.value == "alice@example.com" })
        assertTrue(card.item.fields.any { it.label == "What was the name of First Car?" && it.value == "Ford" })
        assertTrue(card.item.notes.contains("个人账号"))
        assertTrue(card.item.notes.contains("工作用途"))
        assertEquals("iVBORw0KGgo=", card.item.iconData)
        assertEquals("custom", card.item.iconKey)
    }

    @Test
    fun `safeincloud xml export round trips`() {
        val vault = Vault(name = "导出测试")
        val label = com.safeincloud.core.model.Label(name = "工作")
        vault.labels.add(label)
        val item = VaultItem(
            title = "GitHub",
            url = "https://github.com",
            fields = mutableListOf(
                Field(label = "用户名", value = "alice", type = FieldType.TEXT),
                Field(label = "密码", value = "s3cret", type = FieldType.PASSWORD)
            ),
            notes = "个人账号"
        )
        item.favorite = true
        item.labelIds.add(label.id)
        item.iconKey = "custom"
        item.iconData = "iVBORw0KGgo="
        vault.items.add(item)

        val xml = SafeInCloudXml.export(vault)
        assertTrue(xml.contains("<database"))
        val parsed = SafeInCloudXml.parse(xml)
        assertEquals(1, parsed.size)
        val card = parsed.first()
        assertEquals("GitHub", card.item.title)
        assertEquals("alice", card.item.primaryUsername)
        assertEquals("s3cret", card.item.primaryPassword)
        assertEquals("https://github.com", card.item.url)
        assertEquals("个人账号", card.item.notes)
        assertTrue(card.item.favorite)
        assertEquals(listOf("工作"), card.labelNames)
        assertEquals("iVBORw0KGgo=", card.item.iconData)
    }
}
