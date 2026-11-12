package com.yaoahos.safepassword.android.ui

import com.yaoahos.safepassword.android.data.AppSettings
import com.yaoahos.safepassword.android.data.BackupEntry
import com.yaoahos.safepassword.android.update.ReleaseNote
import com.yaoahos.safepassword.android.update.UpdateInfo
import com.yaoahos.safepassword.core.model.FieldType
import com.yaoahos.safepassword.core.model.Vault
import com.yaoahos.safepassword.core.model.VaultItem
import com.yaoahos.safepassword.core.model.VaultMeta
import com.yaoahos.safepassword.core.security.SecurityReport
import com.yaoahos.safepassword.core.sync.CloudVaultEntry
import com.yaoahos.safepassword.core.sync.WebDavConfig
import java.util.Locale

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
    /**
     * 这条同步状态属于哪个页面（false = 首页，true = WebDAV 页）。
     *
     * 两个页面都会显示同步状态，状态本身是全局的；不区分归属的话，
     * 首页发起的同步会飘到 WebDAV 页的「保存后自动同步」下面，反之亦然。
     */
    val syncStatusInWebDav: Boolean = false,
    val syncConflict: Boolean = false,
    val backups: List<BackupEntry> = emptyList(),
    /** 「从云端恢复」列出的云端密码库（来自云端清单）。 */
    val cloudVaults: List<CloudVaultEntry> = emptyList(),
    val cloudVaultsLoading: Boolean = false,
    val fingerprintAvailable: Boolean = false,
    val biometricSaved: Boolean = false,
    /** 新建/导入密码库后，提示用户开启指纹解锁。 */
    val fingerprintPrompt: Boolean = false,
    /**
     * 同步时发现云端已删除当前密码库（另一端删的）。
     * 非空表示要弹窗让用户选「删除本地」还是「恢复到云端」。
     */
    val remoteDeletedName: String? = null,
    /**
     * 同步时发现云端已有同名但不同 key 的密码库。
     * 非空表示要弹窗让用户给本机这个库改名后再同步。
     */
    val nameConflictMessage: String? = null,
    /**
     * 是否还存在没解决的「云端同名库」冲突。
     *
     * 与 [nameConflictMessage] 分开：只关掉弹窗不会清掉这个标记，
     * 所以下次同步（解锁、回前台、手动同步）再撞上时还会重新弹出。
     */
    val nameConflictPending: Boolean = false,
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
    // 用 Locale.ROOT：默认 locale 在土耳其语等环境会把 I 折成 ı，导致搜索匹配不上。
    val query = state.search.trim().lowercase(Locale.ROOT)
    var items = vault.items.filter {
        it.status == com.yaoahos.safepassword.core.model.ItemStatus.ACTIVE
    }
    // 过滤用的标签必须在当前库里真的存在；否则（换了密码库、或标签被其它设备
    // 删掉后同步下来）就当作没有筛选，避免首页莫名变成 0 条。
    val labelId = state.filterLabelId?.takeIf { id -> vault.labels.any { it.id == id } }
    if (labelId != null) {
        items = items.filter { labelId in it.labelIds }
    }
    if (state.onlyFavorites) {
        items = items.filter { it.favorite }
    }
    if (query.isNotBlank()) {
        items = items.filter { item ->
            item.title.lowercase(Locale.ROOT).contains(query) ||
                item.subtitle.lowercase(Locale.ROOT).contains(query) ||
                item.url.lowercase(Locale.ROOT).contains(query) ||
                item.notes.lowercase(Locale.ROOT).contains(query) ||
                item.fields.any { field ->
                    // 文件字段的 value 是 base64，搜索它没有意义，也别把内容泄漏进搜索结果。
                    field.type != FieldType.FILE &&
                        (state.settings.searchPasswords ||
                            (field.type != FieldType.PASSWORD && field.type != FieldType.SECRET &&
                                field.type != FieldType.TOTP)) &&
                        field.value.lowercase(Locale.ROOT).contains(query)
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
    // 先升序排，再整体反转来表示降序——与桌面端 `sort(升序)` + `reverse()` 完全同构。
    // 不能写成 comparator.reversed()：时间戳打平时稳定排序会保留原顺序，
    // 而桌面端的整体反转会把并列那一段也翻过去，两端就成镜像了。
    val sorted = items.sortedWith(comparator)
    val ordered = if (state.sortAscending) sorted else sorted.reversed()
    return ordered.sortedByDescending { it.favorite }
}

fun trashedItems(state: AppUiState): List<VaultItem> =
    state.activeVault?.items.orEmpty()
        .filter { it.status == com.yaoahos.safepassword.core.model.ItemStatus.TRASHED }
        .sortedByDescending { it.deletedAt ?: it.updatedAt }
