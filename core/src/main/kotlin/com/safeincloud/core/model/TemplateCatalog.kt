package com.safeincloud.core.model

data class FieldDef(
    val label: String,
    val type: FieldType = FieldType.TEXT,
    val sensitive: Boolean = type == FieldType.PASSWORD
)

data class TemplateDef(
    val id: String,
    val name: String,
    val description: String,
    val fields: List<FieldDef>
)

object TemplateCatalog {
    const val LOGIN = "login"
    const val BANK_CARD = "bank_card"
    const val EMAIL = "email"
    const val WIFI = "wifi"
    const val IDENTITY = "identity"
    const val NOTE = "note"
    const val CUSTOM = "custom"

    val templates: List<TemplateDef> = listOf(
        TemplateDef(
            LOGIN, "登录", "网站、应用账号",
            listOf(
                FieldDef("账号", FieldType.TEXT),
                FieldDef("密码", FieldType.PASSWORD),
                FieldDef("网址", FieldType.URL)
            )
        ),
        TemplateDef(
            BANK_CARD, "银行卡", "银行卡、信用卡",
            listOf(
                FieldDef("卡号", FieldType.TEXT, sensitive = true),
                FieldDef("持卡人", FieldType.TEXT),
                FieldDef("有效期", FieldType.TEXT),
                FieldDef("CVV", FieldType.PASSWORD),
                FieldDef("银行", FieldType.TEXT)
            )
        ),
        TemplateDef(
            EMAIL, "邮箱", "电子邮箱账号",
            listOf(
                FieldDef("邮箱", FieldType.EMAIL),
                FieldDef("密码", FieldType.PASSWORD),
                FieldDef("服务器", FieldType.TEXT)
            )
        ),
        TemplateDef(
            WIFI, "Wi-Fi", "无线网络密码",
            listOf(
                FieldDef("网络名称", FieldType.TEXT),
                FieldDef("密码", FieldType.PASSWORD),
                FieldDef("加密方式", FieldType.TEXT)
            )
        ),
        TemplateDef(
            IDENTITY, "证件", "身份证、护照、驾照",
            listOf(
                FieldDef("姓名", FieldType.TEXT),
                FieldDef("证件号", FieldType.TEXT, sensitive = true),
                FieldDef("有效期", FieldType.TEXT),
                FieldDef("签发机关", FieldType.TEXT)
            )
        ),
        TemplateDef(NOTE, "安全笔记", "PIN、许可证、机密文本", emptyList()),
        TemplateDef(CUSTOM, "自定义", "自由添加字段", emptyList())
    )

    fun find(id: String): TemplateDef = templates.firstOrNull { it.id == id } ?: templates.last()

    fun displayName(id: String): String = find(id).name

    fun createItem(templateId: String, title: String = ""): VaultItem {
        val template = find(templateId)
        return VaultItem(
            templateId = template.id,
            title = title,
            fields = template.fields.map { def ->
                Field(label = def.label, type = def.type, sensitive = def.sensitive)
            }.toMutableList()
        )
    }
}
