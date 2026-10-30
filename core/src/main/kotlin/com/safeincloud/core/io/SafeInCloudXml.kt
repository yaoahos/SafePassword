package com.safeincloud.core.io

import com.safeincloud.core.model.Field
import com.safeincloud.core.model.FieldType
import com.safeincloud.core.model.ItemStatus
import com.safeincloud.core.model.Label
import com.safeincloud.core.model.TemplateCatalog
import com.safeincloud.core.model.Vault
import com.safeincloud.core.model.VaultItem
import com.safeincloud.core.model.now
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** A card parsed from a SafeInCloud XML export, with label names rather than numeric ids. */
data class SafeInCloudCard(
    val item: VaultItem,
    val labelNames: List<String>
)

/**
 * Parser for the XML format exported by the original SafeInCloud app.
 *
 * The format is a flat <database> with <label id=".." name=".."/> entries and
 * <card ...> entries containing <field>, <label_id>, <notes>, <image> and <file>.
 * File attachments are intentionally not stored here; fields, labels, notes,
 * image, URL, favorite and deleted/template flags are preserved.
 */
object SafeInCloudXml {
    fun looksLikeXml(text: String): Boolean = text.trimStart('\uFEFF', ' ', '\n', '\r', '\t').startsWith("<")

    fun parse(text: String): List<SafeInCloudCard> {
        val document = parseDocument(text)
        val root = document.documentElement ?: error("XML 没有根节点")
        require(root.tagName.equals("database", ignoreCase = true)) {
            "不是 SafeInCloud 导出的 XML（根节点应为 database）"
        }

        val labels = root.childElements("label").mapNotNull { label ->
            val id = label.attr("id").trim()
            val name = label.attr("name").trim()
            if (id.isBlank() || name.isBlank()) null else id to name
        }.toMap()

        return root.childElements("card").mapNotNull { card ->
            parseCard(card, labels)
        }
    }

    /**
     * Export the active vault in the flat XML format used by the original
     * SafeInCloud client, so it can be imported there again.
     */
    fun export(vault: Vault): String {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument()
        val root = document.createElement("database")
        document.appendChild(root)

        val labels: List<Label> = vault.labels
        val labelIds = labels.mapIndexed { index, label -> label.id to (index + 1).toString() }.toMap()
        labels.forEach { label ->
            val element = document.createElement("label")
            element.setAttribute("id", labelIds.getValue(label.id))
            element.setAttribute("name", label.name)
            root.appendChild(element)
        }

        vault.items.asSequence()
            .filter { it.status == ItemStatus.ACTIVE }
            .forEach { item ->
                appendCard(document, root, item, labelIds)
            }

        val transformer = TransformerFactory.newInstance().newTransformer()
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8")
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        val writer = StringWriter()
        transformer.transform(DOMSource(document), StreamResult(writer))
        return writer.toString()
    }

    private fun appendCard(
        document: Document,
        root: Element,
        item: VaultItem,
        labelIds: Map<String, String>
    ) {
        val card = document.createElement("card")
        card.setAttribute("id", item.id)
        card.setAttribute("title", item.title.ifBlank { "未命名" })
        card.setAttribute("time_stamp", (item.updatedAt / 1000L).toString())
        card.setAttribute("template", "false")
        if (item.favorite) card.setAttribute("star", "true")

        val hasUrlField = item.fields.any {
            it.type == FieldType.URL ||
                it.label.equals("网址", ignoreCase = true) ||
                it.label.equals("地址", ignoreCase = true) ||
                it.label.contains("website", ignoreCase = true) ||
                it.label.contains("url", ignoreCase = true)
        }
        item.fields.forEach { field ->
            if (field.label.equals("备注", ignoreCase = true)) return@forEach
            appendField(document, card, field.label.ifBlank { "自定义字段" }, exportType(field), field.value)
        }
        if (item.url.isNotBlank() && !hasUrlField) {
            appendField(document, card, "website", "url", item.url)
        }
        item.labelIds.mapNotNull { labelIds[it] }.distinct().forEach { id ->
            val labelRef = document.createElement("label_id")
            labelRef.textContent = id
            card.appendChild(labelRef)
        }
        if (item.notes.isNotBlank()) {
            val notes = document.createElement("notes")
            notes.textContent = item.notes
            card.appendChild(notes)
        }
        if (item.iconData.isNotBlank()) {
            val icon = document.createElement("custom_icon")
            icon.textContent = item.iconData
                .substringAfter("base64,", item.iconData)
                .filterNot { it.isWhitespace() }
            card.appendChild(icon)
        } else if (item.iconKey.isNotBlank() && item.iconKey != "custom") {
            card.setAttribute("symbol", item.iconKey)
        }
        root.appendChild(card)
    }

    private fun appendField(document: Document, card: Element, name: String, type: String, value: String) {
        val field = document.createElement("field")
        field.setAttribute("name", name)
        field.setAttribute("type", type)
        field.textContent = value
        card.appendChild(field)
    }

    private fun exportType(field: Field): String {
        val label = field.label.lowercase()
        return when {
            label.contains("密码") -> "password"
            label.contains("卡号") -> "card_number"
            label.contains("持卡人") -> "card_holder"
            label.contains("有效期") -> "expiry"
            label.contains("cvv") -> "cvv"
            label.contains("银行") -> "bank"
            label.contains("网络名称") || label.contains("wifi") -> "network_name"
            label.contains("证件号") -> "identity_number"
            label.contains("用户名") || label.contains("账号") || label.contains("帐号") ||
                label.contains("登录") -> "login"
            label.contains("邮箱") || field.type == FieldType.EMAIL -> "email"
            label.contains("秘密") -> "secret"
            label.contains("电话") || field.type == FieldType.PHONE -> "phone"
            label.contains("网址") || label.contains("url") || label.contains("website") ||
                field.type == FieldType.URL -> "website"
            else -> when (field.type) {
                FieldType.PASSWORD -> "password"
                FieldType.SECRET -> "secret"
                FieldType.EMAIL -> "email"
                FieldType.PHONE -> "phone"
                FieldType.URL -> "website"
                else -> "text"
            }
        }
    }

    private fun parseCard(card: Element, labels: Map<String, String>): SafeInCloudCard? {
        if (card.attr("template").isTrue() || card.attr("deleted").isTrue()) return null

        val title = card.attr("title").trim().ifBlank { "未命名" }
        val fields = mutableListOf<Field>()
        var url = ""
        val noteParts = mutableListOf<String>()

        card.childElements("field").forEach { element ->
            val type = element.attr("type").trim().lowercase()
            val rawName = element.attr("name").trim()
            val value = element.textContent.orEmpty().trim()
            if (rawName.isBlank() && value.isBlank() && type.isBlank()) return@forEach

            val label = safeLabel(type, rawName, value)
            if (label.isBlank()) return@forEach
            val fieldType = safeType(type, rawName)
            if (type == "notes") {
                if (value.isNotBlank()) noteParts.add(value)
                return@forEach
            }
            if (fieldType == FieldType.URL && url.isBlank() && value.isNotBlank()) {
                url = value
            }
            fields.add(
                Field(
                    label = label,
                    value = value,
                    type = fieldType,
                    sensitive = fieldType == FieldType.PASSWORD || fieldType == FieldType.SECRET
                )
            )
        }

        val notesFromElement = card.childElement("notes")?.textContent.orEmpty().trim()
        if (notesFromElement.isNotBlank()) noteParts.add(0, notesFromElement)

        val labelNames = card.childElements("label_id").mapNotNull { element ->
            val id = element.textContent.orEmpty().trim()
            if (id.isBlank()) null else labels[id] ?: "标签 $id"
        }.distinct()

        val timestamp = card.attr("time_stamp").toLongOrNull()?.let { raw ->
            if (raw in 1..9_999_999_999L) raw * 1000 else raw
        } ?: now()

        val templateId = when {
            fields.any { it.label == "卡号" } -> TemplateCatalog.BANK_CARD
            fields.any { it.label == "网络名称" } -> TemplateCatalog.WIFI
            fields.any { it.label == "证件号" } -> TemplateCatalog.IDENTITY
            fields.isEmpty() && notesFromElement.isNotBlank() -> TemplateCatalog.NOTE
            else -> TemplateCatalog.LOGIN
        }

        val customIcon = (card.getElementsByTagName("custom_icon").item(0) as? Element)
            ?.textContent.orEmpty().trim()
        val imageNode = (card.getElementsByTagName("image").item(0) as? Element)
            ?.textContent.orEmpty().trim()
        val rawImage = customIcon.ifBlank { imageNode }
        val image = rawImage
            .substringAfter("base64,", rawImage)
            .filterNot { it.isWhitespace() }
        val symbol = card.attr("symbol").trim()
        val item = VaultItem(
            templateId = templateId,
            title = title,
            fields = fields.toMutableList(),
            notes = noteParts.joinToString("\n\n"),
            url = url,
            labelIds = mutableListOf(),
            favorite = card.attr("star").isTrue(),
            createdAt = timestamp,
            updatedAt = timestamp,
            iconKey = when {
                image.isNotBlank() -> "custom"
                symbol.isNotBlank() -> symbol
                else -> ""
            },
            iconData = image
        )
        return SafeInCloudCard(item, labelNames)
    }

    private fun safeType(type: String, rawName: String): FieldType = when (type) {
        "password", "cvv", "pin" -> FieldType.PASSWORD
        "secret" -> FieldType.SECRET
        "url", "website" -> FieldType.URL
        "email", "e-mail" -> FieldType.EMAIL
        "phone", "tel" -> FieldType.PHONE
        "multiline", "notes", "note" -> FieldType.MULTILINE
        "login", "username", "user" ->
            if (rawName.contains("email", ignoreCase = true) || rawName.contains("邮箱")) {
                FieldType.EMAIL
            } else {
                FieldType.TEXT
            }
        else -> when {
            rawName.contains("网址") || rawName == "地址" ||
                rawName.contains("url", ignoreCase = true) ||
                rawName.contains("website", ignoreCase = true) -> FieldType.URL
            else -> FieldType.TEXT
        }
    }

    private fun safeLabel(type: String, rawName: String, value: String): String {
        val normalized = rawName.trim().lowercase()
        fun generic(vararg names: String) = normalized.isBlank() || names.any { normalized == it }
        return when (type) {
            "login", "username", "user" -> when {
                normalized.contains("email") || normalized.contains("邮箱") -> "邮箱"
                generic("login", "username", "user", "account", "账号", "帐号", "登录名", "登录账号") -> "用户名"
                else -> rawName
            }
            "password" -> if (generic("password", "pass", "pwd", "密码")) "密码" else rawName
            "secret" -> if (generic("secret", "秘密")) "秘密" else rawName
            "url", "website" -> if (generic("website", "url", "web site", "网址")) "网址" else rawName
            "email", "e-mail" -> "邮箱"
            "phone", "tel" -> if (generic("phone", "tel", "telephone", "电话")) "电话" else rawName
            "card_number" -> "卡号"
            "card_holder" -> "持卡人"
            "expiry", "expiration" -> "有效期"
            "cvv" -> "CVV"
            "bank" -> "银行"
            "network_name", "ssid" -> "网络名称"
            "identity_number" -> "证件号"
            "notes", "note" -> "备注"
            else -> rawName.ifBlank {
                if (value.isBlank()) "" else type.replace('_', ' ').ifBlank { "自定义字段" }
            }
        }
    }

    private fun parseDocument(text: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isExpandEntityReferences = false
        runCatching {
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
        }
        runCatching {
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        runCatching {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        }
        runCatching {
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        val builder = factory.newDocumentBuilder()
        return builder.parse(InputSource(StringReader(text.trimStart('\uFEFF'))))
    }
}

private fun Element.childElements(tag: String): List<Element> {
    val result = mutableListOf<Element>()
    val nodes = childNodes
    for (index in 0 until nodes.length) {
        val node = nodes.item(index)
        if (node is Element && node.tagName.equals(tag, ignoreCase = true)) {
            result.add(node)
        }
    }
    return result
}

private fun Element.childElement(tag: String): Element? {
    val nodes = childNodes
    for (index in 0 until nodes.length) {
        val node = nodes.item(index)
        if (node is Element && node.tagName.equals(tag, ignoreCase = true)) return node
    }
    return null
}

private fun Element.attr(name: String): String = getAttribute(name).orEmpty()

private fun String.isTrue(): Boolean = equals("true", ignoreCase = true) || this == "1"
