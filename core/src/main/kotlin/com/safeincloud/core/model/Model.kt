package com.safeincloud.core.model

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

fun now(): Long = System.currentTimeMillis()

@Serializable
enum class ItemStatus {
    ACTIVE,
    ARCHIVED,
    TRASHED
}

@Serializable
enum class FieldType {
    TEXT,
    PASSWORD,
    URL,
    EMAIL,
    PHONE,
    MULTILINE
}

@Serializable
data class Field(
    val id: String = newId(),
    var label: String,
    var value: String = "",
    var type: FieldType = FieldType.TEXT,
    var sensitive: Boolean = type == FieldType.PASSWORD
)

@Serializable
data class AttachmentRef(
    val id: String = newId(),
    var name: String,
    var mime: String = "application/octet-stream",
    var size: Long = 0,
    var addedAt: Long = now()
)

@Serializable
data class Label(
    val id: String = newId(),
    var name: String,
    /** ARGB color integer. */
    var color: Int = 0xFF0F766E.toInt(),
    /** 数字越小越靠前；用于密码库顶部标签排序。 */
    var sortOrder: Int = 0
)

@Serializable
data class VaultItem(
    val id: String = newId(),
    var templateId: String = TemplateCatalog.LOGIN,
    var title: String = "",
    var fields: MutableList<Field> = mutableListOf(),
    var notes: String = "",
    /** Editable metadata for the notes row in the item editor. */
    var notesLabel: String = "备注",
    var notesType: FieldType = FieldType.MULTILINE,
    /** Position among item fields; -1 means after all fields. */
    var notesPosition: Int = -1,
    var url: String = "",
    var labelIds: MutableList<String> = mutableListOf(),
    var favorite: Boolean = false,
    var status: ItemStatus = ItemStatus.ACTIVE,
    var createdAt: Long = now(),
    var updatedAt: Long = now(),
    var deletedAt: Long? = null,
    var attachments: MutableList<AttachmentRef> = mutableListOf(),
    /** Preset icon id: letter / key / card / chat / work / custom. Empty = template default. */
    var iconKey: String = "",
    /** Base64 encoded square PNG used when [iconKey] == "custom". */
    var iconData: String = ""
) {
    val primaryPassword: String?
        get() = fields.firstOrNull { it.type == FieldType.PASSWORD }?.value?.takeIf { it.isNotBlank() }

    val primaryUsername: String?
        get() {
            val nonEmpty = fields.filter { it.value.isNotBlank() }
            fun exact(vararg labels: String): String? = nonEmpty.firstOrNull { field ->
                labels.any { field.label.equals(it, ignoreCase = true) }
            }?.value

            return exact("账号", "帐号", "账户")
                ?: exact("用户名", "登录名", "登录账号")
                ?: exact("邮箱", "电子邮件", "Email", "e-mail")
                ?: nonEmpty.firstOrNull { field ->
                    val label = field.label
                    label.contains("账号") || label.contains("帐号") ||
                        label.contains("账户") || label.contains("用户名") ||
                        label.contains("邮箱") || label.contains("邮件") ||
                        label.contains("email", ignoreCase = true) || label.contains("登录")
                }?.value
                ?: exact("卡号", "证件号")
        }

    val subtitle: String
        get() = primaryUsername ?: url.takeIf { it.isNotBlank() } ?: notes.lineSequence().firstOrNull().orEmpty()

    fun field(label: String): Field? = fields.firstOrNull { it.label == label }

    fun copyAsConflictCopy(): VaultItem = copy(
        id = newId(),
        title = title.ifBlank { "未命名" } + "（冲突副本）",
        favorite = false,
        status = ItemStatus.ACTIVE,
        createdAt = now(),
        updatedAt = now(),
        deletedAt = null,
        fields = fields.map { it.copy(id = newId()) }.toMutableList(),
        attachments = attachments.map { it.copy() }.toMutableList(),
        labelIds = labelIds.toMutableList()
    )

    fun copyAsDuplicate(): VaultItem = copy(
        id = newId(),
        title = title.ifBlank { "未命名" } + " 副本",
        favorite = false,
        status = ItemStatus.ACTIVE,
        createdAt = now(),
        updatedAt = now(),
        deletedAt = null,
        fields = fields.map { it.copy(id = newId()) }.toMutableList(),
        attachments = attachments.map { it.copy() }.toMutableList(),
        labelIds = labelIds.toMutableList()
    )
}

@Serializable
data class Vault(
    val schema: Int = 1,
    val id: String = newId(),
    var name: String = "我的密码库",
    var items: MutableList<VaultItem> = mutableListOf(),
    var labels: MutableList<Label> = mutableListOf(),
    var createdAt: Long = now(),
    var updatedAt: Long = now(),
    var revision: Long = 0,
    var lastSyncedAt: Long = 0,
    var deviceId: String = newId(),
    var contentKey: String = ""
) {
    fun item(id: String): VaultItem? = items.firstOrNull { it.id == id }

    fun label(id: String): Label? = labels.firstOrNull { it.id == id }

    fun activeItems(): List<VaultItem> = items.filter { it.status == ItemStatus.ACTIVE }

    fun archivedItems(): List<VaultItem> = items.filter { it.status == ItemStatus.ARCHIVED }

    fun trashedItems(): List<VaultItem> = items.filter { it.status == ItemStatus.TRASHED }

    fun touch() {
        updatedAt = now()
        revision += 1
    }
}

@Serializable
data class VaultMeta(
    val id: String,
    var name: String,
    val fileName: String,
    val createdAt: Long = now(),
    var lastOpenedAt: Long = 0,
    var lastSyncedAt: Long = 0
)

@Serializable
data class VaultIndex(
    var vaults: MutableList<VaultMeta> = mutableListOf()
)
