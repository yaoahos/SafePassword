package com.yaoahos.safepassword.android.ui

import com.yaoahos.safepassword.core.io.XmlCard
import com.yaoahos.safepassword.core.model.Label
import com.yaoahos.safepassword.core.model.Vault
import com.yaoahos.safepassword.core.model.newId

/**
 * 与 ViewModel 状态无关的纯变换。
 *
 * 放在这里而不是 AppViewModel 内，是为了让条目处理逻辑可以被单独阅读和复用，
 * 也让 AppViewModel 只保留状态编排职责。
 */

/**
 * 把导入的图标按统一规则归一化，与用户手动选图走同一套逻辑（见 [IconCodec]）。
 *
 * 旧实现会把任意比例的图强行拉成 128×128，导入长条 logo 会变形；现在改成
 * 4:3 以内居中裁切、超出范围等比留白，目标 192px，体积超限再降 128。
 */
internal fun compactIconData(raw: String): String = IconCodec.normalizeIconData(raw)

/** 把兼容格式的 XML 卡片并入密码库，返回 (新增条目数, 新增标签数)。 */
internal fun mergeXmlCards(vault: Vault, cards: List<XmlCard>): Pair<Int, Int> {
    val labelsByName = vault.labels.associateBy { it.name }.toMutableMap()
    var newLabels = 0
    // 新标签沿用文件里的排序；文件没带排序时追加到末尾。
    var nextOrder = (vault.labels.maxOfOrNull { it.sortOrder } ?: -1) + 1
    cards.forEach { card ->
        val labelIds = card.labelRefs.mapNotNull { ref ->
            val existing = labelsByName[ref.name]
            val label = if (existing != null) {
                existing
            } else {
                val created = Label(
                    name = ref.name,
                    sortOrder = ref.sortOrder ?: nextOrder++
                ).let { created -> val color = ref.color; if (color != null) created.copy(color = color) else created }
                vault.labels.add(created)
                labelsByName[ref.name] = created
                newLabels++
                created
            }
            label.id
        }
        val item = card.item
        item.fields = item.fields.map { it.copy(id = newId()) }.toMutableList()
        item.iconData = compactIconData(item.iconData)
        item.labelIds = labelIds.toMutableList()
        vault.items.add(item)
    }
    return cards.size to newLabels
}
