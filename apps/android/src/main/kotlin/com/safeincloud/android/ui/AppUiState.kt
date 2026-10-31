package com.safeincloud.android.ui

import com.safeincloud.android.data.AppSettings
import com.safeincloud.android.update.ReleaseNote
import com.safeincloud.android.update.UpdateInfo
import com.safeincloud.core.model.FieldType
import com.safeincloud.core.model.Vault
import com.safeincloud.core.model.VaultItem
import com.safeincloud.core.model.VaultMeta
import com.safeincloud.core.security.SecurityReport
import com.safeincloud.core.storage.BackupInfo
import com.safeincloud.core.sync.WebDavConfig

data class AppUiState(
    val initialized: Boolean = false,
    val vaults: List<VaultMeta> = emptyList(),
    val activeVault: Vault? = null,
    val activeMeta: VaultMeta? = null,
    val unlocked: Boolean = false,
    val screen: Screen = Screen.Unlock,
    val backStack: List<Screen> = emptyList(),
    val originTab: Screen = Screen.Vault,
    /** 最近一次页面切换的方向：true 为进入下一层，false 为返回上一层或切换标签页。 */
    val navForward: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val search: String = "",
    val sortMode: SortMode = SortMode.CREATED,
    /** 排序方向：true 为升序，false 为降序。时间类排序默认降序（新条目在前）。 */
    val sortAscending: Boolean = false,
    val filterLabelId: String? = null,
    val onlyFavorites: Boolean = false,
    val security: SecurityReport = SecurityReport(),
    val breached: Map<String, Int> = emptyMap(),
    val settings: AppSettings = AppSettings(),
    val webDav: WebDavConfig = WebDavConfig(),
    val syncStatus: String = "",
    val syncStatusAt: Long = 0L,
    val syncConflict: Boolean = false,
    val backups: List<BackupInfo> = emptyList(),
    val fingerprintAvailable: Boolean = false,
    val biometricSaved: Boolean = false,
    val dataVersion: Long = 0,
    // ---- 检查更新 / 自更新 ----
    val updateChecking: Boolean = false,
    val updateInfo: UpdateInfo? = null,
    val updateDialogVisible: Boolean = false,
    val updateDownloading: Boolean = false,
    /** -1 表示没有进行中的下载；0..100 为下载进度。 */
    val updateProgress: Int = -1,
    /** 已下载完成的安装包路径；非空时提示安装。 */
    val updateReadyFile: String? = null,
    val changelog: List<ReleaseNote> = emptyList(),
    val showChangelog: Boolean = false
)

fun visibleItems(state: AppUiState): List<VaultItem> {
    val vault = state.activeVault ?: return emptyList()
    val query = state.search.trim().lowercase()
    var items = vault.items.filter {
        it.status == com.safeincloud.core.model.ItemStatus.ACTIVE
    }
    val labelId = state.filterLabelId
    if (!labelId.isNullOrBlank()) {
        items = items.filter { labelId in it.labelIds }
    }
    if (state.onlyFavorites) {
        items = items.filter { it.favorite }
    }
    if (query.isNotBlank()) {
        items = items.filter { item ->
            item.title.lowercase().contains(query) ||
                item.subtitle.lowercase().contains(query) ||
                item.url.lowercase().contains(query) ||
                item.notes.lowercase().contains(query) ||
                item.fields.any { field ->
                    (state.settings.searchPasswords ||
                        (field.type != FieldType.PASSWORD && field.type != FieldType.SECRET)) &&
                        field.value.lowercase().contains(query)
                }
        }
    }
    val comparator = when (state.sortMode) {
        SortMode.TITLE -> {
            // 中文按拼音、英文按字母且忽略大小写。
            // Collator 不是线程安全对象，因此每次排序单独创建一个。
            val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)
            java.util.Comparator<VaultItem> { a, b -> collator.compare(a.title, b.title) }
        }
        SortMode.UPDATED -> java.util.Comparator<VaultItem> { a, b -> a.updatedAt.compareTo(b.updatedAt) }
        SortMode.CREATED -> java.util.Comparator<VaultItem> { a, b -> a.createdAt.compareTo(b.createdAt) }
    }
    val sorted = items.sortedWith(if (state.sortAscending) comparator else comparator.reversed())
    return sorted.sortedByDescending { it.favorite }
}

fun trashedItems(state: AppUiState): List<VaultItem> =
    state.activeVault?.items.orEmpty()
        .filter { it.status == com.safeincloud.core.model.ItemStatus.TRASHED }
        .sortedByDescending { it.deletedAt ?: it.updatedAt }
