package com.yaoahos.safepassword.core

import com.yaoahos.safepassword.core.io.VaultXml
import com.yaoahos.safepassword.core.io.VaultCodec
import com.yaoahos.safepassword.core.model.Field
import com.yaoahos.safepassword.core.model.FieldType
import com.yaoahos.safepassword.core.model.TemplateCatalog
import com.yaoahos.safepassword.core.model.Vault
import com.yaoahos.safepassword.core.model.VaultItem
import com.yaoahos.safepassword.core.security.GeneratorOptions
import com.yaoahos.safepassword.core.security.PasswordGenerator
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
    fun `xml import keeps fields labels favorite and skips templates`() {
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
        val cards = VaultXml.parse(xml)
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
    fun `xml export round trips`() {
        val vault = Vault(name = "导出测试")
        val label = com.yaoahos.safepassword.core.model.Label(name = "工作")
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

        val xml = VaultXml.export(vault)
        assertTrue(xml.contains("<database"))
        val parsed = VaultXml.parse(xml)
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

    @Test
    fun `xml export keeps label color and order`() {
        val vault = Vault(name = "标签测试")
        val first = com.yaoahos.safepassword.core.model.Label(
            name = "工作",
            color = 0xFF2563EB.toInt(),
            sortOrder = 0
        )
        val second = com.yaoahos.safepassword.core.model.Label(
            name = "社交",
            color = 0xFFDB2777.toInt(),
            sortOrder = 3
        )
        vault.labels.add(first)
        vault.labels.add(second)
        val item = VaultItem(
            title = "某站点",
            fields = mutableListOf(Field(label = "用户名", value = "u", type = FieldType.TEXT))
        )
        item.labelIds.add(first.id)
        item.labelIds.add(second.id)
        vault.items.add(item)

        val xml = VaultXml.export(vault)
        assertTrue(xml, xml.contains("color=\"#2563EB\""))
        assertTrue(xml, xml.contains("sort-order=\"3\""))
        val refs = VaultXml.parse(xml).first().labelRefs.associateBy { it.name }
        assertEquals(0xFF2563EB.toInt(), refs.getValue("工作").color)
        assertEquals(3, refs.getValue("社交").sortOrder)
    }

    @Test
    fun `xml export keeps secret field type`() {
        val vault = Vault(name = "秘密字段")
        val item = VaultItem(
            title = "服务器",
            fields = mutableListOf(
                Field(label = "恢复码", value = "AAAA-BBBB", type = FieldType.SECRET, sensitive = true)
            )
        )
        vault.items.add(item)

        val card = VaultXml.parse(VaultXml.export(vault)).first()
        val secret = card.item.fields.first { it.label == "恢复码" }
        assertEquals(FieldType.SECRET, secret.type)
        assertTrue(secret.sensitive)
    }

    @Test
    fun `csv export keeps label color and order`() {
        val vault = Vault(name = "CSV 标签")
        val label = com.yaoahos.safepassword.core.model.Label(
            name = "工作",
            color = 0xFF2563EB.toInt(),
            sortOrder = 4
        )
        vault.labels.add(label)
        val item = VaultItem(title = "某站点", fields = mutableListOf(Field(label = "用户名", value = "u", type = FieldType.TEXT)))
        item.labelIds.add(label.id)
        vault.items.add(item)

        val rows = VaultCodec.rowsFromCsv(VaultCodec.toCsv(vault))
        val ref = rows.single().labels.single()
        assertEquals("工作", ref.name)
        assertEquals(0xFF2563EB.toInt(), ref.color)
        assertEquals(4, ref.sortOrder)
    }
    @Test
    fun `json export round trips label color and field types`() {
        val vault = Vault(name = "JSON 测试")
        val label = com.yaoahos.safepassword.core.model.Label(
            name = "银行",
            color = 0xFFDC2626.toInt(),
            sortOrder = 2
        )
        vault.labels.add(label)
        val item = VaultItem(
            title = "招商银行",
            fields = mutableListOf(
                Field(label = "密码", value = "p@ss", type = FieldType.PASSWORD, sensitive = true),
                Field(label = "恢复码", value = "XYZ", type = FieldType.SECRET, sensitive = true)
            )
        )
        item.labelIds.add(label.id)
        vault.items.add(item)

        val back = VaultCodec.fromJson(VaultCodec.toJson(vault))
        assertEquals(0xFFDC2626.toInt(), back.labels.single().color)
        assertEquals(2, back.labels.single().sortOrder)
        val types = back.items.single().fields.map { it.type }
        assertEquals(listOf(FieldType.PASSWORD, FieldType.SECRET), types)
        assertEquals(listOf(back.labels.single().id), back.items.single().labelIds)
    }
}
