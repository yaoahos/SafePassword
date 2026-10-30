package com.safeincloud.desktop

import com.safeincloud.core.crypto.VaultContainer
import com.safeincloud.core.io.SafeInCloudXml
import com.safeincloud.core.io.VaultCodec
import com.safeincloud.core.model.ItemStatus
import com.safeincloud.core.model.Label
import com.safeincloud.core.model.VaultItem
import com.safeincloud.core.model.VaultMeta
import com.safeincloud.core.model.now
import com.safeincloud.core.security.HibpClient
import com.safeincloud.core.security.SecurityAnalyzer
import com.safeincloud.core.security.SecurityReport
import com.safeincloud.core.storage.BackupInfo
import com.safeincloud.core.storage.BackupManager
import com.safeincloud.core.storage.VaultStore
import com.safeincloud.core.sync.ConflictStrategy
import com.safeincloud.core.sync.SyncDirection
import com.safeincloud.core.sync.SyncEngine
import com.safeincloud.core.sync.SyncResult
import com.safeincloud.core.sync.WebDavClient
import com.safeincloud.core.sync.WebDavConfig
import com.safeincloud.desktop.data.DesktopPaths
import com.safeincloud.desktop.data.DesktopSettings
import com.safeincloud.desktop.data.SettingsStore
import io.ktor.client.engine.java.Java
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI

enum class DesktopScreen { Unlock, Vault }

/** 侧边栏分类。 */
enum class SidebarSection(val title: String) {
    All("所有项目"),
    Favorites("收藏夹"),
    Archived("已归档"),
    Trash("回收站"),
    Security("密码安全"),
    Tools("工具"),
    Settings("设置"),
    Sync("WebDAV 同步"),
    About("关于")
}

enum class SortMode { Title, Updated, Created }

data class DesktopUiState(
    val initialized: Boolean = false,
    val vaults: List<VaultMeta> = emptyList(),
    val activeMeta: VaultMeta? = null,
    val screen: DesktopScreen = DesktopScreen.Unlock,
    val vault: com.safeincloud.core.model.Vault? = null,
    val section: SidebarSection = SidebarSection.All,
    val filterLabelId: String? = null,
    val search: String = "",
    val sortMode: SortMode = SortMode.Title,
    val selectedItemId: String? = null,
    val editingItem: VaultItem? = null,
    val creatingItem: Boolean = false,
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val settings: DesktopSettings = DesktopSettings(),
    val security: SecurityReport? = null,
    val breaches: Map<String, Int> = emptyMap(),
    val checkingBreaches: Boolean = false,
    val syncStatus: String = "",
    val syncBusy: Boolean = false,
    val backups: List<BackupInfo> = emptyList(),
    val updateInfo: UpdateInfo? = null,
    val updateChecking: Boolean = false,
    val changelog: List<ReleaseNote> = emptyList()
)

data class UpdateInfo(val version: String, val notes: String, val publishedAt: String, val pageUrl: String)
data class ReleaseNote(val version: String, val name: String, val notes: String, val date: String)

enum class ImportKind { SafeInCloudXml, Csv, Json }
enum class ExportKind { SafeInCloudXml, Csv, Json }

class AppViewModel {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vaultStore = VaultStore(DesktopPaths.root)
    private val backupManager = BackupManager(DesktopPaths.backups)
    private val engine = Java.create()

    private var container: VaultContainer? = null
    private var password: CharArray? = null
    private var attachmentStore: com.safeincloud.core.storage.AttachmentStore? = null

    /** 附件存储需在解锁后按内容密钥建立，这里做一次缓存并复用。 */
    private fun attachments(): com.safeincloud.core.storage.AttachmentStore? {
        container?.let { c -> attachmentStore?.let { return it } }
        val c = container ?: return null
        val meta = _state.value.activeMeta ?: return null
        return com.safeincloud.core.storage.AttachmentStore(vaultStore.attachmentsDir(meta.id), c.contentKey)
            .also { attachmentStore = it }
    }

    private val _state = MutableStateFlow(DesktopUiState())
    val state: StateFlow<DesktopUiState> = _state.asStateFlow()

    init {
        val settings = SettingsStore.load()
        _state.update { it.copy(settings = settings, initialized = true) }
        refreshVaults()
    }

    fun refreshVaults() {
        val vaults = vaultStore.list()
        _state.update { state ->
            val active = state.activeMeta?.let { current -> vaults.firstOrNull { it.id == current.id } }
                ?: vaults.firstOrNull()
            state.copy(vaults = vaults, activeMeta = active)
        }
    }

    fun selectVault(meta: VaultMeta) {
        _state.update { it.copy(activeMeta = meta) }
    }

    // ---------------------------------------------------------------- unlock

    fun createVault(name: String, password: CharArray, onDone: (Boolean) -> Unit) {
        scope.launch {
            try {
                val (c, meta) = vaultStore.create(name, password)
                container = c
                this@AppViewModel.password = password.copyOf()
                _state.update {
                    it.copy(screen = DesktopScreen.Vault, vault = c.vault, activeMeta = meta, message = "密码库已创建")
                }
                refreshVaults()
                withContext(Dispatchers.Main) { onDone(true) }
            } catch (e: Exception) {
                showError("创建失败：${e.message}")
                withContext(Dispatchers.Main) { onDone(false) }
            }
        }
    }

    fun unlock(meta: VaultMeta, password: CharArray, onDone: (Boolean) -> Unit) {
        scope.launch {
            _state.update { it.copy(busy = true) }
            try {
                val c = vaultStore.open(meta, password)
                container = c
                this@AppViewModel.password = password.copyOf()
                _state.update {
                    it.copy(
                        screen = DesktopScreen.Vault,
                        vault = c.vault,
                        activeMeta = meta,
                        busy = false,
                        selectedItemId = c.vault.activeItems().firstOrNull()?.id,
                        settings = it.settings.copy(lastVaultId = meta.id)
                    )
                }
                SettingsStore.save(_state.value.settings)
                withContext(Dispatchers.Main) { onDone(true) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false) }
                showError(e.message ?: "解锁失败")
                withContext(Dispatchers.Main) { onDone(false) }
            }
        }
    }

    fun lock() {
        persistNow(silent = true)
        container = null
        attachmentStore = null
        password?.fill(' ')
        password = null
        _state.update {
            it.copy(
                screen = DesktopScreen.Unlock,
                vault = null,
                selectedItemId = null,
                editingItem = null,
                creatingItem = false,
                security = null,
                syncStatus = ""
            )
        }
        refreshVaults()
    }

    // ----------------------------------------------------------------- vault

    fun vaultFile(): java.nio.file.Path? {
        val meta = _state.value.activeMeta ?: return null
        return vaultStore.vaultFile(meta)
    }

    private fun persistNow(silent: Boolean = false) {
        val c = container ?: return
        val meta = _state.value.activeMeta ?: return
        runCatching { vaultStore.save(c, meta) }
            .onFailure { if (!silent) showError("保存失败：${it.message}") }
    }

    private fun persist() {
        persistNow()
        if (_state.value.settings.autoSync && _state.value.settings.webDav.enabled) {
            syncNow()
        }
    }

    // ---------------------------------------------------------------- attachments

    fun addAttachment(itemId: String, file: java.io.File) {
        val store = attachments() ?: run { showError("请先解锁密码库"); return }
        val item = _state.value.vault?.item(itemId) ?: return
        scope.launch {
            try {
                val name = file.name.ifBlank { "附件" }
                val mime = java.nio.file.Files.probeContentType(file.toPath())
                    ?: guessMime(file.name.lowercase())
                val ref = file.inputStream().use { store.add(it, name, mime) }
                item.attachments.add(ref)
                item.updatedAt = com.safeincloud.core.model.now()
                _state.value.vault?.touch()
                persist()
                _state.update { it.copy(message = "已添加附件：$name") }
            } catch (e: Exception) {
                showError("添加附件失败：${e.message}")
            }
        }
    }

    fun openAttachment(ref: com.safeincloud.core.model.AttachmentRef) {
        val store = attachments() ?: return
        scope.launch {
            try {
                val bytes = store.read(ref)
                val dir = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "SafePassword")
                java.nio.file.Files.createDirectories(dir)
                val out = dir.resolve(sanitizeFileName(ref.name))
                java.nio.file.Files.write(out, bytes)
                java.awt.Desktop.getDesktop().open(out.toFile())
            } catch (e: Exception) {
                showError("无法打开附件：${e.message}")
            }
        }
    }

    fun saveAttachment(ref: com.safeincloud.core.model.AttachmentRef, target: java.io.File) {
        val store = attachments() ?: return
        scope.launch {
            try {
                val bytes = store.read(ref)
                java.nio.file.Files.write(target.toPath(), bytes)
                _state.update { it.copy(message = "已保存：${target.name}") }
            } catch (e: Exception) {
                showError("保存附件失败：${e.message}")
            }
        }
    }

    fun deleteAttachment(itemId: String, ref: com.safeincloud.core.model.AttachmentRef) {
        val store = attachments() ?: return
        val item = _state.value.vault?.item(itemId) ?: return
        scope.launch {
            runCatching { store.delete(ref.id) }
            item.attachments.removeAll { it.id == ref.id }
            item.updatedAt = com.safeincloud.core.model.now()
            _state.value.vault?.touch()
            persist()
            _state.update { it.copy(message = "已删除附件") }
        }
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "attachment" }

    private fun guessMime(lower: String): String = when {
        lower.endsWith(".pdf") -> "application/pdf"
        lower.endsWith(".png") -> "image/png"
        lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
        lower.endsWith(".gif") -> "image/gif"
        lower.endsWith(".txt") -> "text/plain"
        lower.endsWith(".csv") -> "text/csv"
        lower.endsWith(".zip") -> "application/zip"
        else -> "application/octet-stream"
    }

    fun refreshBackups() {
        _state.update { it.copy(backups = backupManager.list()) }
    }

    fun backupNow() {
        val meta = _state.value.activeMeta ?: return
        val file = vaultFile() ?: return
        scope.launch {
            try {
                persistNow(silent = true)
                val info = backupManager.backup(file, meta.name)
                backupManager.prune(20)
                refreshBackups()
                _state.update { it.copy(message = "已备份：${info.name}") }
            } catch (e: Exception) {
                showError("备份失败：${e.message}")
            }
        }
    }

    fun restoreBackup(backup: BackupInfo) {
        val meta = _state.value.activeMeta ?: return
        scope.launch {
            try {
                backupManager.restore(backup.path, vaultStore.vaultFile(meta))
                _state.update { it.copy(message = "已恢复备份，需要重新解锁") }
                lock()
            } catch (e: Exception) {
                showError("恢复失败：${e.message}")
            }
        }
    }

    fun deleteBackup(backup: BackupInfo) {
        scope.launch {
            backupManager.delete(backup)
            refreshBackups()
        }
    }

    // ----------------------------------------------------------------- items

    fun visibleItems(): List<VaultItem> {
        val v = _state.value.vault ?: return emptyList()
        val s = _state.value
        var items = when (s.section) {
            SidebarSection.Trash -> v.trashedItems()
            SidebarSection.Archived -> v.archivedItems()
            SidebarSection.Favorites -> v.activeItems().filter { it.favorite }
            else -> v.activeItems()
        }
        val labelId = s.filterLabelId
        if (labelId != null) {
            items = items.filter { it.labelIds.contains(labelId) }
        }
        val query = s.search.trim()
        if (query.isNotEmpty()) {
            val lower = query.lowercase()
            items = items.filter { item ->
                item.title.lowercase().contains(lower) ||
                    item.subtitle.lowercase().contains(lower) ||
                    item.notes.lowercase().contains(lower) ||
                    item.fields.any { field ->
                        field.label.lowercase().contains(lower) ||
                            ((s.settings.searchPasswords ||
                                (field.type != com.safeincloud.core.model.FieldType.PASSWORD &&
                                    field.type != com.safeincloud.core.model.FieldType.SECRET)) &&
                                field.value.lowercase().contains(lower))
                    }
            }
        }
        return when (s.sortMode) {
            SortMode.Title -> items.sortedBy { it.title.lowercase() }
            SortMode.Updated -> items.sortedByDescending { it.updatedAt }
            SortMode.Created -> items.sortedByDescending { it.createdAt }
        }
    }

    fun selectItem(id: String?) {
        _state.update { it.copy(selectedItemId = id, editingItem = null, creatingItem = false) }
    }

    fun selected(): VaultItem? {
        val id = _state.value.selectedItemId ?: return null
        return _state.value.vault?.item(id)
    }

    fun startEdit() {
        val item = selected() ?: return
        _state.update { it.copy(editingItem = deepCopy(item), creatingItem = false) }
    }

    fun startCreate(templateId: String? = null) {
        val item = if (templateId != null) {
            com.safeincloud.core.model.TemplateCatalog.createItem(templateId)
        } else {
            VaultItem()
        }
        _state.update { it.copy(editingItem = item, creatingItem = true) }
    }

    fun updateEditing(block: (VaultItem) -> Unit) {
        _state.update { state ->
            val item = state.editingItem ?: return@update state
            block(item)
            state.copy(editingItem = item)
        }
    }

    fun cancelEdit() {
        _state.update { it.copy(editingItem = null, creatingItem = false) }
    }

    fun saveEditing() {
        val c = container ?: return
        val item = _state.value.editingItem ?: return
        val creating = _state.value.creatingItem
        scope.launch {
            try {
                item.updatedAt = now()
                val vault = c.vault
                if (creating) {
                    item.createdAt = now()
                    vault.items.add(item)
                } else {
                    val index = vault.items.indexOfFirst { it.id == item.id }
                    if (index >= 0) vault.items[index] = item else vault.items.add(item)
                }
                vault.touch()
                _state.update {
                    it.copy(
                        vault = c.vault,
                        editingItem = null,
                        creatingItem = false,
                        selectedItemId = item.id,
                        message = "已保存"
                    )
                }
                persist()
            } catch (e: Exception) {
                showError("保存失败：${e.message}")
            }
        }
    }

    fun deleteItem(item: VaultItem) {
        val c = container ?: return
        scope.launch {
            val vault = c.vault
            if (item.status == ItemStatus.TRASHED) {
                vault.items.remove(item)
            } else {
                item.status = ItemStatus.TRASHED
                item.deletedAt = now()
            }
            vault.touch()
            _state.update {
                it.copy(vault = c.vault, selectedItemId = null, editingItem = null, message = "已移到回收站")
            }
            persist()
        }
    }

    fun restoreItem(item: VaultItem) {
        val c = container ?: return
        scope.launch {
            item.status = ItemStatus.ACTIVE
            item.deletedAt = null
            c.vault.touch()
            _state.update { it.copy(vault = c.vault, message = "已恢复") }
            persist()
        }
    }

    fun purgeItem(item: VaultItem) {
        val c = container ?: return
        scope.launch {
            c.vault.items.remove(item)
            c.vault.touch()
            _state.update { it.copy(vault = c.vault, message = "已彻底删除") }
            persist()
        }
    }

    fun toggleFavorite(item: VaultItem) {
        val c = container ?: return
        scope.launch {
            item.favorite = !item.favorite
            c.vault.touch()
            _state.update { it.copy(vault = c.vault) }
            persist()
        }
    }

    fun archiveItem(item: VaultItem) {
        val c = container ?: return
        scope.launch {
            item.status = if (item.status == ItemStatus.ARCHIVED) ItemStatus.ACTIVE else ItemStatus.ARCHIVED
            c.vault.touch()
            _state.update { it.copy(vault = c.vault, message = "已更新归档状态") }
            persist()
        }
    }

    fun duplicateItem(item: VaultItem) {
        val c = container ?: return
        scope.launch {
            val copy = item.copyAsDuplicate()
            c.vault.items.add(copy)
            c.vault.touch()
            _state.update { it.copy(vault = c.vault, selectedItemId = copy.id, message = "已创建副本") }
            persist()
        }
    }

    // ---------------------------------------------------------------- labels

    fun addLabel(name: String, color: Int) {
        val c = container ?: return
        if (name.isBlank()) return
        scope.launch {
            c.vault.labels.add(Label(name = name.trim(), color = color, sortOrder = c.vault.labels.size))
            c.vault.touch()
            _state.update { it.copy(vault = c.vault, message = "已创建标签") }
            persist()
        }
    }

    fun renameLabel(label: Label, name: String) {
        val c = container ?: return
        if (name.isBlank()) return
        scope.launch {
            label.name = name.trim()
            c.vault.touch()
            _state.update { it.copy(vault = c.vault) }
            persist()
        }
    }

    fun deleteLabel(label: Label) {
        val c = container ?: return
        scope.launch {
            c.vault.labels.remove(label)
            c.vault.items.forEach { it.labelIds.remove(label.id) }
            c.vault.touch()
            _state.update { state ->
                state.copy(
                    vault = c.vault,
                    filterLabelId = if (state.filterLabelId == label.id) null else state.filterLabelId
                )
            }
            persist()
        }
    }

    fun toggleItemLabel(item: VaultItem, labelId: String) {
        val c = container ?: return
        scope.launch {
            if (item.labelIds.contains(labelId)) item.labelIds.remove(labelId) else item.labelIds.add(labelId)
            item.updatedAt = now()
            c.vault.touch()
            _state.update { it.copy(vault = c.vault) }
            persist()
        }
    }

    // -------------------------------------------------------- import/export

    fun importItemsFromText(text: String, kind: ImportKind) {
        val c = container ?: return
        scope.launch {
            try {
                val vault = c.vault
                var count = 0
                when (kind) {
                    ImportKind.SafeInCloudXml -> {
                        val cards = SafeInCloudXml.parse(text)
                        for (card in cards) {
                            val ids = card.labelNames.map { name ->
                                vault.labels.firstOrNull { it.name == name }?.id
                                    ?: run {
                                        val label = Label(name = name)
                                        vault.labels.add(label)
                                        label.id
                                    }
                            }.toMutableList()
                            card.item.labelIds = ids
                            vault.items.add(card.item)
                            count++
                        }
                    }
                    ImportKind.Csv -> {
                        val items = VaultCodec.itemsFromCsv(text)
                        vault.items.addAll(items)
                        count = items.size
                    }
                    ImportKind.Json -> {
                        val imported = VaultCodec.fromJson(text)
                        vault.items.addAll(imported.items)
                        imported.labels.forEach { label ->
                            if (vault.labels.none { it.name == label.name }) vault.labels.add(label)
                        }
                        count = imported.items.size
                    }
                }
                vault.touch()
                _state.update { it.copy(vault = c.vault, message = "已导入 $count 个项目") }
                persist()
            } catch (e: Exception) {
                showError("导入失败：${e.message}")
            }
        }
    }

    fun exportItems(kind: ExportKind): String? {
        val v = _state.value.vault ?: return null
        return try {
            when (kind) {
                ExportKind.SafeInCloudXml -> SafeInCloudXml.export(v)
                ExportKind.Csv -> VaultCodec.toCsv(v)
                ExportKind.Json -> VaultCodec.toJson(v)
            }
        } catch (e: Exception) {
            showError("导出失败：${e.message}")
            null
        }
    }

    // ------------------------------------------------------------------ sync

    fun updateWebDav(config: WebDavConfig) {
        _state.update { state -> state.copy(settings = state.settings.copy(webDav = config)) }
        SettingsStore.save(_state.value.settings)
    }

    fun updateSettings(block: (DesktopSettings) -> DesktopSettings) {
        _state.update { state -> state.copy(settings = block(state.settings)) }
        SettingsStore.save(_state.value.settings)
    }

    private fun buildSyncEngine(): SyncEngine? {
        val c = container ?: return null
        val meta = _state.value.activeMeta ?: return null
        val config = _state.value.settings.webDav
        if (!config.isConfigured()) return null
        val webDav = WebDavClient(config, engine)
        val attachments = com.safeincloud.core.storage.AttachmentStore(
            vaultStore.attachmentsDir(meta.id),
            c.contentKey
        )
        return SyncEngine(webDav, config, attachments, meta.id, meta.name)
    }

    fun testWebDav() {
        val config = _state.value.settings.webDav
        scope.launch {
            _state.update { it.copy(syncBusy = true, syncStatus = "正在测试连接…") }
            val webDav = WebDavClient(config, engine)
            try {
                webDav.test()
                _state.update { it.copy(syncBusy = false, syncStatus = "连接成功") }
            } catch (e: Exception) {
                _state.update { it.copy(syncBusy = false, syncStatus = "连接失败：${e.message}") }
            } finally {
                webDav.close()
            }
        }
    }

    fun syncNow(strategy: ConflictStrategy = ConflictStrategy.MERGE) {
        val c = container ?: return
        val pw = password ?: return
        val syncEngine = buildSyncEngine()
        if (syncEngine == null) {
            _state.update { it.copy(syncStatus = "请先配置 WebDAV") }
            return
        }
        scope.launch {
            _state.update { it.copy(syncBusy = true, syncStatus = "正在同步…") }
            try {
                val result = syncEngine.sync(c, pw, strategy)
                container = result.container
                _state.update {
                    it.copy(vault = result.container.vault, syncBusy = false, syncStatus = syncText(result))
                }
                if (result.direction != SyncDirection.CONFLICT) {
                    val meta = _state.value.activeMeta
                    if (meta != null) vaultStore.save(result.container, meta)
                }
            } catch (e: Exception) {
                _state.update { it.copy(syncBusy = false, syncStatus = "同步失败：${e.message}") }
            }
        }
    }

    fun pullRemote() {
        val c = container ?: return
        val pw = password ?: return
        val syncEngine = buildSyncEngine()
        if (syncEngine == null) {
            _state.update { it.copy(syncStatus = "请先配置 WebDAV") }
            return
        }
        scope.launch {
            _state.update { it.copy(syncBusy = true, syncStatus = "正在拉取云端数据…") }
            try {
                val result = syncEngine.pullRemote(c, pw)
                container = result.container
                _state.update {
                    it.copy(vault = result.container.vault, syncBusy = false, syncStatus = "已用云端数据覆盖本地")
                }
                val meta = _state.value.activeMeta
                if (meta != null) vaultStore.save(result.container, meta)
            } catch (e: Exception) {
                _state.update { it.copy(syncBusy = false, syncStatus = "拉取失败：${e.message}") }
            }
        }
    }

    private fun syncText(result: SyncResult): String = when (result.direction) {
        SyncDirection.NONE -> "已是最新状态"
        SyncDirection.UPLOADED -> "已上传本地更新"
        SyncDirection.DOWNLOADED -> "已下载远端更新"
        SyncDirection.MERGED ->
            if (result.conflicts > 0) "已合并，产生 ${result.conflicts} 个冲突副本" else "已合并本地与远端更新"
        SyncDirection.CONFLICT -> "检测到本地与云端都有新修改，请选择处理方式"
    }

    // -------------------------------------------------------------- security

    fun refreshSecurity() {
        val v = _state.value.vault ?: return
        _state.update { it.copy(security = SecurityAnalyzer.analyze(v, _state.value.breaches)) }
    }

    fun checkBreaches() {
        val v = _state.value.vault ?: return
        scope.launch {
            _state.update { it.copy(checkingBreaches = true) }
            val client = HibpClient(engine)
            try {
                val counts = mutableMapOf<String, Int>()
                val items = v.activeItems().mapNotNull { item -> item.primaryPassword?.let { item.id to it } }
                for ((id, pw) in items) {
                    val count = runCatching { client.passwordCount(pw) }.getOrDefault(0)
                    if (count > 0) counts[id] = count
                }
                _state.update { state ->
                    state.copy(
                        breaches = counts,
                        checkingBreaches = false,
                        security = SecurityAnalyzer.analyze(v, counts)
                    )
                }
            } finally {
                client.close()
                _state.update { it.copy(checkingBreaches = false) }
            }
        }
    }

    // ---------------------------------------------------------------- update

    fun checkForUpdates() {
        scope.launch {
            _state.update { it.copy(updateChecking = true) }
            try {
                val updater = GitHubUpdater(engine)
                val latest = updater.latestRelease()
                val notes = updater.recentReleases(limit = 5)
                val current = com.safeincloud.desktop.BuildConfig.APP_VERSION
                val newer = latest?.takeIf { updater.isNewer(it.version, current) }
                _state.update { it.copy(updateInfo = newer, changelog = notes, updateChecking = false) }
                if (newer == null) {
                    _state.update { it.copy(message = "当前已是最新版本（$current）") }
                }
            } catch (e: Exception) {
                _state.update { it.copy(updateChecking = false) }
                showError("检查更新失败：${e.message}")
            }
        }
    }

    fun openReleasePage() {
        val url = _state.value.updateInfo?.pageUrl ?: return
        runCatching { Desktop.getDesktop().browse(URI(url)) }
            .onFailure { showError("无法打开浏览器：${it.message}") }
    }

    // ------------------------------------------------------------------ misc

    fun selectSection(section: SidebarSection) {
        _state.update { it.copy(section = section) }
        if (section == SidebarSection.Security) refreshSecurity()
        if (section == SidebarSection.Tools) refreshBackups()
    }

    fun filterByLabel(id: String?) {
        _state.update { it.copy(filterLabelId = id, section = SidebarSection.All) }
    }

    fun setSearch(query: String) {
        _state.update { it.copy(search = query) }
    }

    fun setSort(mode: SortMode) {
        _state.update { it.copy(sortMode = mode) }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun showError(text: String) {
        _state.update { it.copy(error = text) }
    }

    fun dataDirText(): String = DesktopPaths.root.toString()
}

private fun deepCopy(item: VaultItem): VaultItem = item.copy(
    fields = item.fields.map { it.copy() }.toMutableList(),
    labelIds = item.labelIds.toMutableList(),
    attachments = item.attachments.map { it.copy() }.toMutableList()
)
