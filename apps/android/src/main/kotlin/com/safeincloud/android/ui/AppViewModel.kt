package com.safeincloud.android.ui

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.safeincloud.android.SafeInCloudApplication
import com.safeincloud.android.BuildConfig
import com.safeincloud.android.data.AppSettings
import com.safeincloud.android.security.BiometricHelper
import com.safeincloud.android.update.GitHubUpdater
import com.safeincloud.android.update.UpdateLog
import com.safeincloud.android.update.UpdateNotifications
import com.safeincloud.core.crypto.VaultContainer
import com.safeincloud.core.crypto.WrongPasswordException
import com.safeincloud.core.io.SafeInCloudXml
import com.safeincloud.core.io.VaultCodec
import com.safeincloud.core.model.AttachmentRef
import com.safeincloud.core.model.ItemStatus
import com.safeincloud.core.model.Label
import com.safeincloud.core.model.Vault
import com.safeincloud.core.model.VaultItem
import com.safeincloud.core.model.VaultMeta
import com.safeincloud.core.model.newId
import com.safeincloud.core.model.now
import com.safeincloud.core.security.HibpClient
import com.safeincloud.core.security.PasswordStrength
import com.safeincloud.core.security.SecurityAnalyzer
import com.safeincloud.core.security.SecurityReport
import com.safeincloud.core.storage.AttachmentStore
import com.safeincloud.core.storage.BackupInfo
import com.safeincloud.core.sync.ConflictStrategy
import com.safeincloud.core.sync.SyncDirection
import com.safeincloud.core.sync.SyncEngine
import com.safeincloud.core.sync.WebDavClient
import com.safeincloud.core.sync.WebDavConfig
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SafeInCloudApplication
    private val container = app.container

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var active: VaultContainer? = null
    private var masterPassword: String? = null
    private var attachmentStore: AttachmentStore? = null
    private var backgroundAt: Long = 0
    private var syncJob: Job? = null
    private var updateJob: Job? = null
    private var failedAttempts = 0
    private var lockedUntil = 0L

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val vaults = container.vaultStore.list()
            val stored = container.settings.settings
            // 1.3.x 的通用 biometricEnabled 只对应指纹；人脸解锁已移除。
            val settings = if (stored.biometricEnabled && !stored.fingerprintEnabled) {
                stored.copy(fingerprintEnabled = true)
            } else {
                stored
            }
            if (settings != stored) container.settings.settings = settings
            val webDav = container.settings.webDav
            val lastId = container.settings.lastVaultId
            val meta = vaults.firstOrNull { it.id == lastId } ?: vaults.firstOrNull()
            val fingerprint = BiometricHelper.status(app)
            _state.update {
                it.copy(
                    initialized = true,
                    vaults = vaults,
                    settings = settings,
                    webDav = webDav,
                    activeMeta = meta,
                    screen = Screen.Unlock,
                    fingerprintAvailable = fingerprint.available,
                    biometricSaved = meta != null && container.biometric.has(meta.id)
                )
            }
        }
    }

    // ---------------------------------------------------------------- vaults

    fun createVault(name: String, password: String) {
        if (password.length < 8) {
            fail("主密码至少需要 8 位")
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (opened, meta) = container.vaultStore.create(name, password.toCharArray())
                container.settings.lastVaultId = meta.id
                attach(opened, meta, password)
                _state.update {
                    it.copy(
                        vaults = container.vaultStore.list(),
                        activeVault = opened.vault,
                        activeMeta = meta,
                        unlocked = true,
                        screen = Screen.Vault,
                        busy = false,
                        filterLabelId = defaultLabelIdFor(opened.vault),
                        security = SecurityAnalyzer.analyze(opened.vault),
                        biometricSaved = false,
                        message = "密码库已创建"
                    )
                }
                maybeAutoBackup(opened, meta)
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun unlock(vaultId: String, password: String) {
        if (password.isBlank()) {
            fail("请输入主密码")
            return
        }
        if (now() < lockedUntil) {
            val seconds = ((lockedUntil - now()) / 1000).coerceAtLeast(1)
            fail("尝试次数过多，请 $seconds 秒后再试")
            return
        }
        val meta = _state.value.vaults.firstOrNull { it.id == vaultId }
        if (meta == null) {
            fail("密码库不存在")
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val opened = container.vaultStore.open(meta, password.toCharArray())
                val updatedMeta = meta.copy(lastOpenedAt = now(), name = opened.vault.name)
                container.vaultStore.updateMeta(updatedMeta)
                container.settings.lastVaultId = meta.id
                attach(opened, updatedMeta, password)
                failedAttempts = 0
                lockedUntil = 0
                _state.update {
                    it.copy(
                        vaults = container.vaultStore.list(),
                        activeVault = opened.vault,
                        activeMeta = updatedMeta,
                        unlocked = true,
                        screen = Screen.Vault,
                        busy = false,
                        filterLabelId = defaultLabelIdFor(opened.vault),
                        security = SecurityAnalyzer.analyze(opened.vault),
                        biometricSaved = container.biometric.has(meta.id),
                        error = null
                    )
                }
                maybeAutoBackup(opened, updatedMeta)
            } catch (e: Exception) {
                if (e is WrongPasswordException) {
                    failedAttempts++
                    if (failedAttempts >= 5) {
                        failedAttempts = 0
                        lockedUntil = now() + 5 * 60_000L
                        fail("连续输错 5 次，已锁定 5 分钟")
                    } else {
                        fail("主密码错误，还可尝试 ${5 - failedAttempts} 次")
                    }
                } else {
                    fail(e)
                }
            }
        }
    }

    fun unlockWithFingerprint() {
        val meta = _state.value.activeMeta ?: run {
            fail("没有可解锁的密码库")
            return
        }
        if (!_state.value.settings.fingerprintEnabled) {
            fail("尚未开启指纹解锁")
            return
        }
        val password = container.biometric.get(meta.id)
        if (password.isNullOrBlank()) {
            fail("尚未为这个密码库保存解锁凭据，请先在设置中开启")
            return
        }
        unlock(meta.id, password)
    }

    fun lock() {
        active = null
        masterPassword = null
        attachmentStore = null
        _state.update {
            it.copy(
                unlocked = false,
                activeVault = null,
                screen = Screen.Unlock,
                busy = false,
                search = "",
                filterLabelId = null,
                backStack = emptyList(),
                originTab = Screen.Vault,
                security = SecurityReport(),
                syncStatus = ""
            )
        }
    }

    fun renameVault(newName: String) {
        val session = active ?: return
        val meta = _state.value.activeMeta ?: return
        val trimmed = newName.trim()
        if (trimmed.isBlank()) {
            fail("名称不能为空")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                session.vault.name = trimmed
                session.vault.touch()
                persist()
                val updated = meta.copy(name = trimmed)
                container.vaultStore.updateMeta(updated)
                _state.update {
                    it.copy(activeMeta = updated, vaults = container.vaultStore.list(), message = "已重命名")
                }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun deleteVault(meta: VaultMeta) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                container.vaultStore.delete(meta)
                container.biometric.clear(meta.id)
                if (_state.value.activeMeta?.id == meta.id) {
                    active = null
                    masterPassword = null
                    attachmentStore = null
                    _state.update {
                        it.copy(
                            activeVault = null,
                            activeMeta = null,
                            unlocked = false,
                            screen = Screen.Unlock,
                            vaults = container.vaultStore.list(),
                            message = "密码库已删除"
                        )
                    }
                } else {
                    _state.update {
                        it.copy(vaults = container.vaultStore.list(), message = "密码库已删除")
                    }
                }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String) {
        val session = active ?: return
        val meta = _state.value.activeMeta ?: return
        if (newPassword.length < 8) {
            fail("新主密码至少需要 8 位")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Verify the old password against the file before re-wrapping.
                VaultContainer.open(container.vaultStore.vaultFile(meta), oldPassword.toCharArray())
                val changed = session.changePassword(newPassword.toCharArray())
                changed.saveTo(container.vaultStore.vaultFile(meta))
                active = changed
                masterPassword = newPassword
                if (container.settings.settings.fingerprintEnabled) {
                    container.biometric.save(meta.id, newPassword)
                }
                _state.update { it.copy(message = "主密码已修改", unlocked = true) }
            } catch (e: Exception) {
                fail("旧主密码不正确，或修改失败")
            }
        }
    }

    // ---------------------------------------------------------------- items

    fun saveItem(
        item: VaultItem,
        navigateBack: Boolean = true,
        returnScreen: Screen? = null
    ) {
        val session = active ?: return
        if (item.title.isBlank()) {
            fail("请填写标题后再进行操作")
            return
        }
        item.title = item.title.trim()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                item.updatedAt = now()
                val index = session.vault.items.indexOfFirst { it.id == item.id }
                if (index >= 0) {
                    session.vault.items[index] = item
                } else {
                    item.createdAt = item.createdAt.takeIf { it > 0 } ?: now()
                    session.vault.items.add(item)
                }
                session.vault.touch()
                persist()
                recomputeSecurity()
                _state.update {
                    if (navigateBack) {
                        // 保存后回到进入编辑页之前的页面：显式指定优先，否则沿返回栈回退一格。
                        val target = returnScreen ?: it.backStack.lastOrNull() ?: Screen.Vault
                        val stack = it.backStack.dropLastWhile { s -> s == target }
                        it.copy(screen = target, backStack = stack, navForward = false, message = "已保存")
                    } else {
                        it.copy(message = "已保存，可以继续添加")
                    }
                }
                scheduleSync()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun deleteItem(itemId: String) {
        changeStatus(itemId, ItemStatus.TRASHED)
    }

    fun deleteItems(itemIds: Set<String>) {
        val session = active ?: return
        if (itemIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val timestamp = now()
                var count = 0
                session.vault.items.filter { it.id in itemIds }.forEach { item ->
                    item.status = ItemStatus.TRASHED
                    item.deletedAt = timestamp
                    item.updatedAt = timestamp
                    count++
                }
                if (count == 0) return@launch
                session.vault.touch()
                persist()
                recomputeSecurity()
                _state.update {
                    it.copy(
                        screen = Screen.Vault,
                        backStack = emptyList(),
                        originTab = Screen.Vault,
                        message = "已将 $count 个条目移入回收站"
                    )
                }
                scheduleSync()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun duplicateItem(itemId: String) {
        val session = active ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val source = session.vault.item(itemId) ?: return@launch
                val copy = source.copyAsDuplicate()
                session.vault.items.add(copy)
                session.vault.touch()
                persist()
                recomputeSecurity()
                _state.update {
                    it.copy(
                        screen = Screen.Vault,
                        message = "已创建副本：${copy.title}"
                    )
                }
                scheduleSync()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun setLabelsForItems(itemIds: Set<String>, labelIds: Set<String>) {
        val session = active ?: return
        if (itemIds.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var count = 0
                session.vault.items.filter { it.id in itemIds }.forEach { item ->
                    item.labelIds = labelIds.toMutableList()
                    item.updatedAt = now()
                    count++
                }
                if (count == 0) return@launch
                session.vault.touch()
                persist()
                recomputeSecurity()
                _state.update {
                    it.copy(
                        screen = Screen.Vault,
                        message = "已为 $count 个条目设置标签"
                    )
                }
                scheduleSync()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun restoreItem(itemId: String) {
        changeStatus(itemId, ItemStatus.ACTIVE)
    }

    fun permanentlyDeleteItem(itemId: String) {
        val session = active ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val removed = session.vault.items.removeAll { it.id == itemId }
                if (removed) {
                    session.vault.touch()
                    persist()
                    recomputeSecurity()
                    _state.update { it.copy(message = "已永久删除") }
                }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun emptyTrash() {
        val session = active ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val count = session.vault.items.count { it.status == ItemStatus.TRASHED }
                if (count == 0) return@launch
                session.vault.items.removeAll { it.status == ItemStatus.TRASHED }
                session.vault.touch()
                persist()
                recomputeSecurity()
                _state.update { it.copy(message = "已清空回收站（$count 条）") }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    private fun changeStatus(itemId: String, status: ItemStatus) {
        val session = active ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val item = session.vault.item(itemId) ?: return@launch
                item.status = status
                item.updatedAt = now()
                item.deletedAt = if (status == ItemStatus.TRASHED) now() else null
                session.vault.touch()
                persist()
                recomputeSecurity()
                _state.update {
                    it.copy(
                        screen = Screen.Vault,
                        backStack = emptyList(),
                        originTab = Screen.Vault,
                        message = when (status) {
                            ItemStatus.TRASHED -> "已移入回收站"
                            ItemStatus.ARCHIVED -> "已恢复"
                            ItemStatus.ACTIVE -> "已恢复"
                        }
                    )
                }
                scheduleSync()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun toggleFavorite(itemId: String) {
        val session = active ?: return
        val index = session.vault.items.indexOfFirst { it.id == itemId }
        if (index < 0) return
        // 用新对象替换旧对象，保证 Compose 能立即观察到星标变化。
        val updated = session.vault.items[index].copy(
            favorite = !session.vault.items[index].favorite,
            updatedAt = now()
        )
        session.vault.items[index] = updated
        session.vault.touch()
        _state.update {
            it.copy(activeVault = session.vault, dataVersion = it.dataVersion + 1)
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                persist()
                scheduleSync()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun addLabel(name: String, color: Int, sortOrder: Int = 0) {
        val session = active ?: return
        if (name.isBlank()) {
            fail("标签名不能为空")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                session.vault.labels.add(
                    Label(name = name.trim(), color = color, sortOrder = sortOrder)
                )
                session.vault.touch()
                persist()
                _state.update { it.copy(message = "已添加标签") }
                scheduleSync()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun updateLabel(label: Label) {
        val session = active ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val index = session.vault.labels.indexOfFirst { it.id == label.id }
            if (index >= 0) {
                session.vault.labels[index] = label
                session.vault.touch()
                persist()
                scheduleSync()
            }
        }
    }

    fun deleteLabel(labelId: String) {
        val session = active ?: return
        viewModelScope.launch(Dispatchers.IO) {
            session.vault.labels.removeAll { it.id == labelId }
            session.vault.items.forEach { it.labelIds.remove(labelId) }
            session.vault.touch()
            persist()
            _state.update {
                it.copy(filterLabelId = it.filterLabelId.takeIf { id -> id != labelId })
            }
            scheduleSync()
        }
    }

    fun deleteLabels(labelIds: Set<String>) {
        if (labelIds.isEmpty()) return
        val session = active ?: return
        viewModelScope.launch(Dispatchers.IO) {
            session.vault.labels.removeAll { it.id in labelIds }
            session.vault.items.forEach { item ->
                item.labelIds.removeAll { it in labelIds }
            }
            session.vault.touch()
            persist()
            _state.update {
                it.copy(
                    filterLabelId = it.filterLabelId.takeIf { id -> id !in labelIds },
                    message = "已删除 ${labelIds.size} 个标签"
                )
            }
            scheduleSync()
        }
    }

    fun toggleItemLabel(itemId: String, labelId: String) {
        val session = active ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val item = session.vault.item(itemId) ?: return@launch
            if (labelId in item.labelIds) item.labelIds.remove(labelId) else item.labelIds.add(labelId)
            item.updatedAt = now()
            session.vault.touch()
            persist()
            scheduleSync()
        }
    }

    fun selectVault(meta: VaultMeta) {
        active = null
        masterPassword = null
        attachmentStore = null
        _state.update {
            it.copy(
                activeMeta = meta,
                unlocked = false,
                activeVault = null,
                screen = Screen.Unlock,
                biometricSaved = container.biometric.has(meta.id)
            )
        }
    }

    // ---------------------------------------------------------------- attachments

    fun addAttachment(item: VaultItem, uri: Uri, onResult: (AttachmentRef?) -> Unit = {}) {
        val store = attachmentStore ?: run {
            fail("请先解锁密码库")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = app.contentResolver
                val name = queryDisplayName(uri) ?: "附件"
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                val ref = resolver.openInputStream(uri)?.use { input ->
                    store.add(input, name, mime)
                }
                if (ref == null) {
                    fail("无法读取附件")
                    withContext(Dispatchers.Main) { onResult(null) }
                } else {
                    item.attachments.add(ref)
                    item.updatedAt = now()
                    withContext(Dispatchers.Main) { onResult(ref) }
                }
            } catch (e: Exception) {
                fail(e)
                withContext(Dispatchers.Main) { onResult(null) }
            }
        }
    }

    fun exportAttachment(ref: AttachmentRef, onResult: (File?) -> Unit = {}) {
        val store = attachmentStore ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = store.read(ref)
                val dir = File(app.cacheDir, "attachments")
                dir.mkdirs()
                val file = File(dir, sanitizeFileName(ref.name))
                file.writeBytes(bytes)
                withContext(Dispatchers.Main) { onResult(file) }
            } catch (e: Exception) {
                fail("无法打开附件：${e.message}")
                withContext(Dispatchers.Main) { onResult(null) }
            }
        }
    }

    fun deleteAttachment(item: VaultItem, ref: AttachmentRef) {
        val store = attachmentStore ?: return
        viewModelScope.launch(Dispatchers.IO) {
            store.delete(ref.id)
            item.attachments.removeAll { it.id == ref.id }
            item.updatedAt = now()
            active?.vault?.let {
                it.touch()
                persist()
                scheduleSync()
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return runCatching {
            app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
    }

    // ---------------------------------------------------------------- filters

    fun setSearch(value: String) = _state.update { it.copy(search = value) }
    fun setSortMode(value: SortMode) = _state.update { it.copy(sortMode = value) }
    fun setFilterLabel(id: String?) = _state.update { it.copy(filterLabelId = id) }
    fun setThemeColor(value: Long) = updateSettings { it.copy(themeColor = value) }
    fun setOnlyFavorites(value: Boolean) = _state.update { it.copy(onlyFavorites = value) }
    fun setSearchPasswords(value: Boolean) = updateSettings { it.copy(searchPasswords = value) }
    fun setDefaultLabel(id: String?) {
        updateSettings { it.copy(defaultLabelId = id) }
        val exists = id != null && _state.value.activeVault?.labels?.any { it.id == id } == true
        _state.update { it.copy(filterLabelId = id.takeIf { exists }) }
    }

    private fun defaultLabelIdFor(vault: Vault?): String? {
        val id = container.settings.settings.defaultLabelId ?: return null
        return id.takeIf { vault?.labels?.any { label -> label.id == id } == true }
    }
    fun navigate(screen: Screen) = _state.update { state ->
        when (screen) {
            Screen.Vault, Screen.Tools, Screen.Unlock -> state.copy(
                screen = screen,
                originTab = if (screen == Screen.Unlock) state.originTab else screen,
                backStack = emptyList(),
                navForward = false
            )
            else -> if (screen == state.screen) state else state.copy(
                screen = screen,
                backStack = (state.backStack + state.screen).takeLast(16),
                navForward = true
            )
        }
    }
    fun consumeMessage() = _state.update { it.copy(message = null) }
    fun consumeError() = _state.update { it.copy(error = null) }
    fun showError(message: String) = fail(message)
    fun showMessage(message: String) = _state.update { it.copy(message = message) }

    fun goBack() {
        _state.update { state ->
            val target = when {
                state.backStack.isNotEmpty() -> state.backStack.last()
                state.screen == Screen.Vault || state.screen == Screen.Tools -> state.screen
                else -> state.originTab
            }
            state.copy(
                screen = target,
                backStack = if (state.backStack.isNotEmpty()) state.backStack.dropLast(1) else emptyList(),
                navForward = false
            )
        }
    }

    // ---------------------------------------------------------------- settings

    fun setAutoLockSeconds(value: Int) = updateSettings { it.copy(autoLockSeconds = value) }
    fun setAutoBackupDays(value: Int) = updateSettings { it.copy(autoBackupDays = value) }
    fun setAutoSync(value: Boolean) = updateSettings { it.copy(autoSync = value) }
    fun setScreenshotAllowed(value: Boolean) = updateSettings { it.copy(screenshotAllowed = value) }
    fun setHibpApiKey(value: String) = updateSettings { it.copy(hibpApiKey = value.trim()) }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(container.settings.settings)
        container.settings.settings = updated
        _state.update { it.copy(settings = updated) }
    }

    fun enableFingerprint() {
        val password = masterPassword ?: run {
            fail("请先解锁密码库")
            return
        }
        val meta = _state.value.activeMeta ?: return
        container.biometric.save(meta.id, password)
        updateSettings { it.copy(fingerprintEnabled = true, biometricEnabled = true) }
        _state.update {
            it.copy(biometricSaved = true, message = "已开启指纹解锁")
        }
    }

    fun disableFingerprint() {
        val meta = _state.value.activeMeta ?: return
        updateSettings { it.copy(fingerprintEnabled = false, biometricEnabled = false) }
        container.biometric.clear(meta.id)
        _state.update {
            it.copy(biometricSaved = false, message = "已关闭指纹解锁")
        }
    }

    // ---------------------------------------------------------------- WebDAV

    fun setWebDav(config: WebDavConfig) {
        container.settings.webDav = config
        _state.update { it.copy(webDav = config) }
    }

    fun testWebDav(config: WebDavConfig) {
        if (!config.isConfigured()) {
            fail("请填写以 http:// 或 https:// 开头的服务器地址")
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val client = WebDavClient(config, OkHttp.create())
            try {
                client.test()
                _state.update { it.copy(busy = false, message = "WebDAV 连接成功") }
            } catch (e: Exception) {
                fail(e.message ?: "连接失败")
            } finally {
                client.close()
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch(Dispatchers.IO) { doSync(silent = false) }
    }

    /** Explicitly replace the local vault with the cloud copy, ignoring timestamps. */
    fun pullFromCloud() {
        val session = active
        val meta = _state.value.activeMeta
        val password = masterPassword
        val config = _state.value.webDav
        if (session == null || meta == null) {
            fail("请先解锁密码库")
            return
        }
        if (password == null) {
            fail("请先解锁密码库")
            return
        }
        if (!config.isConfigured()) {
            fail("请先填写 WebDAV 配置")
            return
        }
        _state.update { it.copy(busy = true, syncStatus = "正在拉取云端数据…", error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val client = WebDavClient(config, OkHttp.create())
            try {
                val store = container.attachmentStore(meta.id, session.contentKey)
                val result = SyncEngine(client, config, store, meta.id, session.vault.name)
                    .pullRemote(session, password.toCharArray())
                active = result.container
                attachmentStore = container.attachmentStore(meta.id, result.container.contentKey)
                val updatedMeta = meta.copy(
                    lastSyncedAt = now(),
                    name = result.container.vault.name
                )
                result.container.saveTo(container.vaultStore.vaultFile(updatedMeta))
                container.vaultStore.updateMeta(updatedMeta)
                _state.update {
                    it.copy(
                        activeVault = result.container.vault,
                        activeMeta = updatedMeta,
                        vaults = container.vaultStore.list(),
                        busy = false,
                        syncStatus = result.message,
                        message = result.message,
                        security = SecurityAnalyzer.analyze(result.container.vault, it.breached),
                        dataVersion = it.dataVersion + 1
                    )
                }
            } catch (e: Exception) {
                fail(e.message ?: "拉取云端数据失败")
            } finally {
                client.close()
            }
        }
    }

    fun resolveSyncConflict(strategy: ConflictStrategy) {
        _state.update { it.copy(syncConflict = false) }
        viewModelScope.launch(Dispatchers.IO) { doSync(silent = false, strategy = strategy) }
    }

    fun dismissSyncConflict() {
        _state.update { it.copy(syncConflict = false, syncStatus = "已取消同步") }
    }

    private fun scheduleSync() {
        if (!_state.value.settings.autoSync) return
        if (!_state.value.webDav.isConfigured()) return
        syncJob?.cancel()
        syncJob = viewModelScope.launch(Dispatchers.IO) {
            delay(1500)
            doSync(silent = true)
        }
    }

    private suspend fun doSync(
        silent: Boolean,
        // 本地修改优先：本地与云端都变化时，以本机版本覆盖云端。
        strategy: ConflictStrategy = ConflictStrategy.KEEP_LOCAL
    ) {
        val session = active ?: return
        val meta = _state.value.activeMeta ?: return
        val password = masterPassword ?: return
        val config = _state.value.webDav
        if (!config.isConfigured()) {
            if (!silent) fail("请先在设置中配置 WebDAV")
            return
        }
        if (!silent) _state.update {
            it.copy(busy = true, syncStatus = "正在同步…", syncStatusAt = now(), error = null)
        }
        val client = WebDavClient(config, OkHttp.create())
        try {
            val store = container.attachmentStore(meta.id, session.contentKey)
            val result = SyncEngine(client, config, store, meta.id, session.vault.name)
                .sync(session, password.toCharArray(), strategy)
            if (result.direction == SyncDirection.CONFLICT) {
                _state.update {
                    it.copy(
                        busy = false,
                        syncConflict = true,
                        syncStatus = result.message,
                        syncStatusAt = now()
                    )
                }
                return
            }
            active = result.container
            attachmentStore = container.attachmentStore(meta.id, result.container.contentKey)
            val updatedMeta = meta.copy(lastSyncedAt = now(), name = result.container.vault.name)
            result.container.saveTo(container.vaultStore.vaultFile(updatedMeta))
            container.vaultStore.updateMeta(updatedMeta)
            _state.update {
                it.copy(
                    activeVault = result.container.vault,
                    activeMeta = updatedMeta,
                    vaults = container.vaultStore.list(),
                    busy = false,
                    syncStatus = result.message,
                    syncStatusAt = now(),
                    message = if (silent) it.message else result.message,
                    security = SecurityAnalyzer.analyze(result.container.vault, it.breached),
                    dataVersion = it.dataVersion + 1
                )
            }
        } catch (e: Exception) {
            if (silent) {
                _state.update {
                    it.copy(
                        busy = false,
                        syncStatus = "自动同步失败：${e.message}",
                        syncStatusAt = now()
                    )
                }
            } else {
                fail(e.message ?: "同步失败")
            }
        } finally {
            client.close()
        }
    }

    // ---------------------------------------------------------------- backup / import / export

    fun backupNow() {
        val session = active ?: return
        val meta = _state.value.activeMeta ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                container.backupManager.backup(container.vaultStore.vaultFile(meta), session.vault.name)
                container.backupManager.prune(10)
                val updated = container.settings.settings.copy(lastBackupAt = now())
                container.settings.settings = updated
                _state.update {
                    it.copy(
                        settings = updated,
                        backups = container.backupManager.list(),
                        message = "备份完成"
                    )
                }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun refreshBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(backups = container.backupManager.list()) }
        }
    }

    fun restoreBackup(info: BackupInfo) {
        val meta = _state.value.activeMeta ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                container.backupManager.restore(info.path, container.vaultStore.vaultFile(meta))
                active = null
                masterPassword = null
                attachmentStore = null
                _state.update {
                    it.copy(
                        unlocked = false,
                        activeVault = null,
                        screen = Screen.Unlock,
                        message = "已从备份恢复，请用该备份的主密码解锁"
                    )
                }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun exportEncrypted(uri: Uri) {
        val session = active ?: return
        writeUri(uri, session.toBytes())
    }

    fun exportPlain(uri: Uri) {
        val session = active ?: return
        writeUri(uri, VaultCodec.toJson(session.vault).toByteArray(Charsets.UTF_8))
    }

    fun exportCsv(uri: Uri) {
        val session = active ?: return
        writeUri(uri, VaultCodec.toCsv(session.vault).toByteArray(Charsets.UTF_8))
    }

    fun exportSafeInCloudXml(uri: Uri) {
        val session = active ?: return
        writeUri(uri, SafeInCloudXml.export(session.vault).toByteArray(Charsets.UTF_8))
    }

    private fun writeUri(uri: Uri, bytes: ByteArray) {
        _state.update { it.copy(busy = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                app.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                    ?: error("无法写入文件")
                _state.update { it.copy(busy = false, message = "已导出 ${bytes.size / 1024} KB") }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun importFile(uri: Uri) = importFiles(listOf(uri))

    fun importFiles(uris: List<Uri>) {
        val session = active ?: return
        if (uris.isEmpty()) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                var importedCount = 0
                var importedLabels = 0
                var failedFiles = 0
                uris.forEach { uri ->
                    val text = try {
                        app.contentResolver.openInputStream(uri)?.use { input ->
                            input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        } ?: error("无法读取文件")
                    } catch (e: Exception) {
                        failedFiles++
                        return@forEach
                    }
                    try {
                        if (SafeInCloudXml.looksLikeXml(text)) {
                            val cards = SafeInCloudXml.parse(text)
                            if (cards.isEmpty()) {
                                failedFiles++
                                return@forEach
                            }
                            val result = mergeSafeInCloudCards(session.vault, cards)
                            importedCount += result.first
                            importedLabels += result.second
                        } else {
                            val importedVault = runCatching { VaultCodec.fromJson(text) }.getOrNull()
                            val items = if (importedVault != null) {
                                importedVault.items.map { item ->
                                    item.copy(
                                        id = newId(),
                                        fields = item.fields.map { it.copy(id = newId()) }.toMutableList(),
                                        attachments = mutableListOf()
                                    )
                                }
                            } else {
                                VaultCodec.itemsFromCsv(text)
                            }
                            if (items.isEmpty()) {
                                failedFiles++
                                return@forEach
                            }
                            session.vault.items.addAll(items)
                            importedCount += items.size
                            if (importedVault != null) {
                                importedVault.labels.forEach { label ->
                                    if (session.vault.labels.none { existing -> existing.name == label.name }) {
                                        session.vault.labels.add(label.copy(id = newId()))
                                        importedLabels++
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        failedFiles++
                    }
                }

                if (importedCount == 0) {
                    error(if (failedFiles > 0) "没有识别到可导入的条目（$failedFiles 个文件无法识别）" else "没有识别到可导入的条目")
                }

                session.vault.touch()
                persist()
                recomputeSecurity()
                _state.update {
                    it.copy(
                        busy = false,
                        screen = Screen.Vault,
                        message = buildString {
                            append("已导入 $importedCount 条")
                            if (importedLabels > 0) append("，新增 $importedLabels 个标签")
                            if (failedFiles > 0) append("，$failedFiles 个文件失败")
                        }
                    )
                }
                scheduleSync()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun importEncrypted(uri: Uri, password: String, name: String) {
        if (password.isBlank()) {
            fail("请输入该数据库的主密码")
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("无法读取文件")
                val (opened, meta) = container.vaultStore.importEncrypted(bytes, password.toCharArray(), name)
                container.settings.lastVaultId = meta.id
                attach(opened, meta, password)
                _state.update {
                    it.copy(
                        vaults = container.vaultStore.list(),
                        activeVault = opened.vault,
                        activeMeta = meta,
                        unlocked = true,
                        screen = Screen.Vault,
                        busy = false,
                        security = SecurityAnalyzer.analyze(opened.vault),
                        message = "已导入保险库"
                    )
                }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun restoreFromCloud(config: WebDavConfig, password: String) {
        val trimmed = "我的密码库"
        if (password.length < 8) {
            fail("请输入该密码库的主密码")
            return
        }
        if (!config.isConfigured()) {
            fail("请先填写 WebDAV 服务器地址")
            return
        }
        container.settings.webDav = config
        _state.update { it.copy(webDav = config, busy = true, error = null, syncStatus = "正在连接云…") }
        viewModelScope.launch(Dispatchers.IO) {
            val client = WebDavClient(config, OkHttp.create())
            try {
                client.test()
                _state.update { it.copy(syncStatus = "云连接成功，正在下载…") }
                val key = config.vaultKey("SafeInCloud")
                val path = config.remotePath(key)
                val bytes = client.get(path) ?: error("云端没有找到密码库文件（$path）")
                val (opened, meta) = container.vaultStore.importEncrypted(bytes, password.toCharArray(), trimmed)
                container.settings.lastVaultId = meta.id
                attach(opened, meta, password)
                _state.update {
                    it.copy(
                        vaults = container.vaultStore.list(),
                        activeVault = opened.vault,
                        activeMeta = meta,
                        unlocked = true,
                        screen = Screen.Vault,
                        busy = false,
                        syncStatus = "",
                        security = SecurityAnalyzer.analyze(opened.vault),
                        message = "已从云端恢复密码库"
                    )
                }
            } catch (e: Exception) {
                fail(e.message ?: "云端恢复失败")
            } finally {
                client.close()
            }
        }
    }

    // ---------------------------------------------------------------- security

    fun checkBreaches() {
        val session = active ?: return
        val index = SecurityAnalyzer.passwordIndex(session.vault).entries.take(50)
        if (index.isEmpty()) {
            fail("没有可检查的密码")
            return
        }
        _state.update { it.copy(busy = true, syncStatus = "检查中 0/${index.size}", error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val client = HibpClient(OkHttp.create())
            try {
                val breached = mutableMapOf<String, Int>()
                index.forEachIndexed { position, entry ->
                    val count = client.passwordCount(entry.key)
                    if (count > 0) entry.value.forEach { breached[it] = count }
                    if (position % 3 == 0) {
                        _state.update { it.copy(syncStatus = "检查中 ${position + 1}/${index.size}") }
                    }
                }
                _state.update {
                    it.copy(
                        busy = false,
                        syncStatus = "",
                        breached = breached,
                        security = SecurityAnalyzer.analyze(session.vault, breached),
                        message = if (breached.isEmpty()) "未发现已泄露密码" else "发现 ${breached.size} 个已泄露密码"
                    )
                }
            } catch (e: Exception) {
                fail(e.message ?: "泄露检查失败")
            } finally {
                client.close()
            }
        }
    }

    fun strengthOf(password: String) = PasswordStrength.evaluate(password)

    // ---------------------------------------------------------------- 检查更新

    /** 查询 GitHub 上最新的正式发布；[showDialog] 为 true 且发现新版本时弹出更新对话框。 */
    fun checkForUpdates(showDialog: Boolean = true) {
        if (_state.value.updateChecking) return
        _state.update { it.copy(updateChecking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val releases = GitHubUpdater.fetchReleases()
                val notes = GitHubUpdater.releaseNotes(releases, limit = 6)
                val published = releases.filter { !it.draft && !it.prerelease && it.tagName.isNotBlank() }
                val latest = published.firstOrNull { release ->
                    release.assets.any { asset -> asset.name.endsWith(".apk", ignoreCase = true) }
                } ?: published.firstOrNull()
                val current = BuildConfig.VERSION_NAME
                if (latest == null) {
                    _state.update {
                        it.copy(updateChecking = false, changelog = notes, message = "未找到可用的发布版本")
                    }
                    return@launch
                }
                val info = GitHubUpdater.toUpdateInfo(latest)
                val newer = GitHubUpdater.isNewer(info.version, current) && info.apkUrl.isNotBlank()
                _state.update {
                    it.copy(
                        updateChecking = false,
                        changelog = notes,
                        updateInfo = info.takeIf { newer },
                        updateDialogVisible = newer && showDialog,
                        message = if (newer) {
                            "发现新版本 v${info.version}"
                        } else {
                            "当前已是最新版本 v$current"
                        }
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(updateChecking = false, message = "检查更新失败：${e.message ?: "网络异常"}")
                }
            }
        }
    }

    fun dismissUpdateDialog() = _state.update { it.copy(updateDialogVisible = false) }

    fun downloadUpdate() {
        val info = _state.value.updateInfo ?: return
        if (_state.value.updateDownloading) return
        if (info.apkUrl.isBlank()) {
            showMessage("该版本没有提供 APK 下载")
            return
        }
        val app = getApplication<Application>()
        _state.update { it.copy(updateDownloading = true, updateProgress = 0, updateDialogVisible = false) }
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            val dir = File(app.cacheDir, "updates")
            val target = File(dir, "SafePassword-${info.version}.apk")
            try {
                dir.mkdirs()
                UpdateNotifications.ensureChannel(app)
                UpdateNotifications.progress(app, -1)
                GitHubUpdater.download(info.apkUrl, target) { percent ->
                    _state.update { it.copy(updateProgress = percent) }
                    UpdateNotifications.progress(app, percent)
                }
                UpdateNotifications.completed(app, info, target.absolutePath)
                _state.update {
                    it.copy(updateDownloading = false, updateProgress = 100, updateReadyFile = target.absolutePath)
                }
            } catch (e: Exception) {
                UpdateNotifications.clear(app)
                _state.update {
                    it.copy(
                        updateDownloading = false,
                        updateProgress = -1,
                        message = "下载失败：${e.message ?: "网络异常"}"
                    )
                }
            }
        }
    }

    fun cancelUpdateDownload() {
        updateJob?.cancel()
        updateJob = null
        UpdateNotifications.clear(getApplication())
        _state.update { it.copy(updateDownloading = false, updateProgress = -1) }
    }

    fun consumeUpdateReady() = _state.update { it.copy(updateReadyFile = null) }

    fun openChangelog() {
        // 更新日志随包内置，离线也能查看，内容只包含功能介绍与问题修复。
        val local = UpdateLog.load(getApplication())
        _state.update {
            it.copy(changelog = local.ifEmpty { it.changelog }, showChangelog = true)
        }
    }

    fun dismissChangelog() = _state.update { it.copy(showChangelog = false) }

    // ---------------------------------------------------------------- lifecycle

    fun onAppBackgrounded() {
        if (_state.value.unlocked) backgroundAt = now()
    }

    fun onAppForegrounded() {
        refreshBiometricAvailability()
        val at = backgroundAt
        backgroundAt = 0
        if (at <= 0 || !_state.value.unlocked) return
        val timeout = _state.value.settings.autoLockSeconds
        if (timeout >= 0 && now() - at >= timeout * 1000L) {
            lock()
        }
    }

    /**
     * 指纹硬件在开机初期、传感器被其它应用占用时会短暂返回不可用。
     * 每次回到前台重新探测一次，避免一次探测失败就把指纹入口永久置灰。
     */
    fun refreshBiometricAvailability() {
        val available = BiometricHelper.status(app).available
        if (available != _state.value.fingerprintAvailable) {
            _state.update { it.copy(fingerprintAvailable = available) }
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun attach(opened: VaultContainer, meta: VaultMeta, password: String) {
        active = opened
        masterPassword = password
        attachmentStore = container.attachmentStore(meta.id, opened.contentKey)
    }

    private fun persist() {
        val session = active ?: return
        val meta = _state.value.activeMeta ?: return
        container.vaultStore.save(session, meta)
        _state.update { it.copy(activeVault = session.vault, dataVersion = it.dataVersion + 1) }
    }

    private fun recomputeSecurity() {
        val session = active ?: return
        _state.update { it.copy(security = SecurityAnalyzer.analyze(session.vault, it.breached)) }
    }

    private fun maybeAutoBackup(session: VaultContainer, meta: VaultMeta) {
        val settings = container.settings.settings
        if (settings.autoBackupDays <= 0) return
        if (now() - settings.lastBackupAt < settings.autoBackupDays * 86_400_000L) return
        runCatching {
            container.backupManager.backup(container.vaultStore.vaultFile(meta), session.vault.name)
            container.backupManager.prune(10)
            val updated = container.settings.settings.copy(lastBackupAt = now())
            container.settings.settings = updated
            _state.update {
                it.copy(settings = updated, backups = container.backupManager.list())
            }
        }
    }

    private fun fail(e: Throwable) {
        fail(e.message ?: "操作失败")
    }

    private fun fail(message: String) {
        _state.update { it.copy(busy = false, syncStatus = "", error = message) }
    }
}
