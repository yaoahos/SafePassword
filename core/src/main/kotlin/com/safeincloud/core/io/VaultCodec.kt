package com.safeincloud.core.io

import com.safeincloud.core.json.AppJson
import com.safeincloud.core.model.Field
import com.safeincloud.core.model.FieldType
import com.safeincloud.core.model.TemplateCatalog
import com.safeincloud.core.model.Vault
import com.safeincloud.core.model.VaultItem
import com.safeincloud.core.model.now

object VaultCodec {
    fun toJson(vault: Vault): String =
        AppJson.instance.encodeToString(Vault.serializer(), vault)

    fun fromJson(text: String): Vault =
        AppJson.instance.decodeFromString(Vault.serializer(), text)

    fun toCsv(vault: Vault): String {
        val builder = StringBuilder()
        builder.append("标题,用户名,密码,网址,备注,标签\r\n")
        vault.items.filter { it.status != com.safeincloud.core.model.ItemStatus.TRASHED }.forEach { item ->
            val username = item.fields.firstOrNull { it.label in listOf("用户名", "邮箱", "账号") }?.value.orEmpty()
            val password = item.primaryPassword.orEmpty()
            val labels = item.labelIds.mapNotNull { id -> vault.label(id)?.name }.joinToString("|")
            builder.append(
                listOf(item.title, username, password, item.url, item.notes, labels)
                    .joinToString(",") { escapeCsv(it) }
            ).append("\r\n")
        }
        return builder.toString()
    }

    fun itemsFromCsv(text: String): List<VaultItem> {
        val rows = parseCsv(text)
        if (rows.isEmpty()) return emptyList()
        val header = rows.first().map { normalizeHeader(it) }
        val titleIndex = header.indexOfFirst { it == "title" }
        val usernameIndex = header.indexOfFirst { it == "username" }
        val passwordIndex = header.indexOfFirst { it == "password" }
        val urlIndex = header.indexOfFirst { it == "url" }
        val notesIndex = header.indexOfFirst { it == "notes" }
        if (titleIndex < 0 && usernameIndex < 0 && passwordIndex < 0) {
            return emptyList()
        }
        return rows.drop(1).mapNotNull { row ->
            fun cell(index: Int): String = if (index in row.indices) row[index].trim() else ""
            val title = cell(titleIndex).ifBlank { cell(usernameIndex) }.ifBlank { "导入的账号" }
            val username = cell(usernameIndex)
            val password = cell(passwordIndex)
            if (username.isBlank() && password.isBlank() && cell(titleIndex).isBlank()) return@mapNotNull null
            val fields = mutableListOf<Field>()
            if (username.isNotBlank()) fields.add(Field(label = "用户名", value = username, type = FieldType.TEXT))
            if (password.isNotBlank()) {
                fields.add(Field(label = "密码", value = password, type = FieldType.PASSWORD, sensitive = true))
            }
            val item = VaultItem(
                templateId = TemplateCatalog.LOGIN,
                title = title,
                fields = fields,
                notes = cell(notesIndex),
                url = cell(urlIndex),
                createdAt = now(),
                updatedAt = now()
            )
            item
        }
    }

    private fun normalizeHeader(header: String): String {
        val value = header.trim().lowercase().replace(" ", "").replace("_", "")
        return when (value) {
            "title", "name", "标题", "名称" -> "title"
            "username", "user", "login", "账号", "用户名", "邮箱" -> "username"
            "password", "pass", "密码" -> "password"
            "url", "website", "网址", "链接" -> "url"
            "notes", "note", "备注", "注释" -> "notes"
            else -> value
        }
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    private fun parseCsv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < text.length) {
            val char = text[index]
            if (inQuotes) {
                when {
                    char == '"' && index + 1 < text.length && text[index + 1] == '"' -> {
                        field.append('"')
                        index++
                    }
                    char == '"' -> inQuotes = false
                    else -> field.append(char)
                }
            } else {
                when (char) {
                    '"' -> inQuotes = true
                    ',' -> {
                        row.add(field.toString())
                        field.clear()
                    }
                    '\n' -> {
                        row.add(field.toString())
                        field.clear()
                        if (row.any { it.isNotBlank() }) rows.add(row)
                        row = mutableListOf()
                    }
                    '\r' -> Unit
                    else -> field.append(char)
                }
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            if (row.any { it.isNotBlank() }) rows.add(row)
        }
        return rows
    }
}
