package com.yaoahos.safepassword.android.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yaoahos.safepassword.android.SafePasswordApplication
import com.yaoahos.safepassword.android.BuildConfig
import com.yaoahos.safepassword.android.data.AppSettings
import com.yaoahos.safepassword.android.data.BackupEntry
import com.yaoahos.safepassword.android.security.BiometricHelper
import com.yaoahos.safepassword.android.update.GitHubUpdater
import com.yaoahos.safepassword.android.update.UpdateLog
import com.yaoahos.safepassword.android.update.UpdateNotifications
import com.yaoahos.safepassword.core.crypto.VaultContainer
import com.yaoahos.safepassword.core.crypto.WrongPasswordException
import com.yaoahos.safepassword.core.io.VaultXml
import com.yaoahos.safepassword.core.io.VaultCodec
import com.yaoahos.safepassword.core.io.AtomicFiles
import com.yaoahos.safepassword.core.model.ItemStatus
import com.yaoahos.safepassword.core.model.Label
import com.yaoahos.safepassword.core.model.Vault
import com.yaoahos.safepassword.core.model.VaultItem
import com.yaoahos.safepassword.core.model.VaultMeta
import com.yaoahos.safepassword.core.model.newId
import com.yaoahos.safepassword.core.model.now
import com.yaoahos.safepassword.core.security.HibpClient
import com.yaoahos.safepassword.core.security.PasswordStrength
import com.yaoahos.safepassword.core.security.SecurityAnalyzer
import com.yaoahos.safepassword.core.security.SecurityReport
import com.yaoahos.safepassword.core.sync.CloudManifest
import com.yaoahos.safepassword.core.sync.CloudVaultEntry
import com.yaoahos.safepassword.core.sync.ConflictStrategy
import com.yaoahos.safepassword.core.sync.SyncDirection
import com.yaoahos.safepassword.core.sync.SyncEngine
import com.yaoahos.safepassword.core.sync.WebDavClient
import com.yaoahos.safepassword.core.sync.WebDavConfig
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.Cipher
import java.io.BufferedReader
import java.io.File

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SafePasswordApplication
    private val container = app.container

    private val _state = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = _state.asStateFlow()

    private var active: VaultContainer? = null
    private var masterPassword: String? = null
    private var backgroundAt: Long = 0
    private var syncJob: Job? = null
    private var updateJob: Job? = null
    /** 自动拉取（轮询云端是否有更新）的后台任务。 */
    private var pullJob: Job? = null
    /** 上一次看到的远端 Last-Modified，0 表示还没取到过。 */
    private var lastRemoteMtime: Long = 0
    /** 上一次实际检查云端的时间，用于限制回前台时的检查频率。 */
    private var lastPullCheckAt: Long = 0
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
            // 主密码明文凭据不会被保存；如果设置里仍标记“已开启”，
            // 会和实际状态不一致（设置页显示已开启、解锁页却没有指纹按钮），这里纠正过来。
            var reconciled = settings
            if (meta != null && reconciled.fingerprintEnabled && !container.biometric.has(meta.id)) {
                reconciled = reconciled.copy(fingerprintEnabled = false, biometricEnabled = false)
                container.settings.settings = reconciled
            }
            val fingerprint = BiometricHelper.status(app)
            _state.update {
                it.copy(
                    initialized = true,
                    vaults = vaults,
                    settings = reconciled,
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
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            fail("请输入密码库名称")
            return
        }
        if (trimmed.length > 40) {
            fail("密码库名称不能超过 40 个字符")
            return
        }
        if (password.length < 8) {
            fail("主密码至少需要 8 位")
            return
        }
        if (_state.value.vaults.any { it.name.equals(trimmed, ignoreCase = true) }) {
            fail("本地已有名为「$trimmed」的密码库，请换一个名称")
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 云端已存在同名库时拒绝创建：否则两个库会算出同一个云端文件名，同步时互相覆盖。
                val config = _state.value.webDav
                if (config.isConfigured()) {
                    val client = WebDavClient(config, OkHttp.create())
                    try {
                        val manifest = CloudManifest.parse(
                            client.get(config.manifestPath())?.toString(Charsets.UTF_8)
                        )
                        if (manifest.vaults.any { it.name.equals(trimmed, ignoreCase = true) }) {
                            fail("云端已有名为「$trimmed」的密码库，请换一个名称，或直接从云端恢复它")
                            _state.update { it.copy(busy = false) }
                            return@launch
                        }
                    } catch (_: Exception) {
                        // 云端不可达不阻塞本地创建；同步时会再校验。
                    } finally {
                        client.close()
                    }
                }
                val (opened, meta) = container.vaultStore.create(trimmed, password.toCharArray())
                container.settings.lastVaultId = meta.id
                attach(opened, meta, password)
                // 新库一开始没有指纹凭据，开关先归零；下面会提示用户是否开启。
                val saved = applyFingerprintState(meta.id)
                _state.update {
                    it.copy(
                        vaults = container.vaultStore.list(),
                        activeVault = opened.vault,
                        activeMeta = meta,
                        unlocked = true,
                        screen = Screen.Vault,
                        busy = false,
                        filterLabelId = defaultLabelIdFor(opened.vault),
                        search = "",
                        onlyFavorites = false,
                        security = SecurityAnalyzer.analyze(opened.vault),
                        fingerprintPrompt = it.fingerprintAvailable && !saved,
                        biometricSaved = saved,
                        message = "密码库已创建"
                    )
                }
                maybeAutoBackup(opened, meta)
                // 新建后立刻推一次云端：即使是空库，也要让另一端能看到，
                // 否则会出现「一端建了库、另一端看不到，等加条目才出现」。
                // 这里不受「保存后自动同步」开关影响——建库本身就是要同步的事件。
                if (_state.value.webDav.isConfigured()) {
                    viewModelScope.launch(Dispatchers.IO) { doSync(silent = true) }
                }
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
                val updatedMeta = meta.copy(lastOpenedAt = now(), name = opened.vault.name, cloudKey = opened.vault.cloudKey.ifBlank { meta.cloudKey })
                container.vaultStore.updateMeta(updatedMeta)
                container.settings.lastVaultId = meta.id
                attach(opened, updatedMeta, password)
                failedAttempts = 0
                lockedUntil = 0
                val saved = applyFingerprintState(meta.id)
                _state.update {
                    it.copy(
                        vaults = container.vaultStore.list(),
                        activeVault = opened.vault,
                        activeMeta = updatedMeta,
                        unlocked = true,
                        screen = Screen.Vault,
                        busy = false,
                        filterLabelId = defaultLabelIdFor(opened.vault),
                        // 换库时清掉上一个库的搜索词与「只看收藏」，
                        // 否则新库首页会莫名其妙是 0 条。
                        search = "",
                        onlyFavorites = false,
                        security = SecurityAnalyzer.analyze(opened.vault),
                        biometricSaved = saved,
                        settings = container.settings.settings,
                        error = null
                    )
                }
                maybeAutoBackup(opened, updatedMeta)
                // 解锁后立刻检查一次云端与本机谁更新，并启动定时拉取。
                restartPullLoop()
                checkRemoteIfDue(force = true)
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

    fun lock() {
        active = null
        masterPassword = null
        pullJob?.cancel()
        pullJob = null
        lastRemoteMtime = 0
        lastPullCheckAt = 0
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
                syncStatus = "",
                // 锁定后一律收起浮层，避免解锁界面上残留更新日志、更新提示或安装询问。
                error = null,
                message = null,
                showChangelog = false,
                updateDialogVisible = false,
                updateReadyFile = null,
                syncConflict = false
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
        if (trimmed.length > 40) {
            fail("密码库名称不能超过 40 个字符")
            return
        }
        if (_state.value.vaults.any { it.id != meta.id && it.name.equals(trimmed, ignoreCase = true) }) {
            fail("本地已有名为「$trimmed」的密码库，请换一个名称")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                session.vault.name = trimmed
                session.vault.touch()
                persist()
                val updated = meta.copy(name = trimmed)
                container.vaultStore.updateMeta(updated)
                // 云端清单里的显示名也立刻改掉，配合随后的一次同步，
                // 另一台设备刷新清单时看到的就已是新名字。
                val cloudRenamed = runCatching {
                    renameCloudEntry(meta.cloudKey, meta.name, trimmed)
                }.isSuccess
                _state.update {
                    it.copy(
                        activeMeta = updated,
                        vaults = container.vaultStore.list(),
                        message = if (cloudRenamed) "已重命名" else "已重命名（云端名稍后同步）"
                    )
                }
                // 库名是同步内容的一部分（云端清单也会更新名字），
                // 不推一次的话另一台设备看到的还是旧名字。
                scheduleSync()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun deleteVault(meta: VaultMeta) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 只清掉这个库自己的指纹凭据。指纹开关随后按「切到的那个库有没有凭据」重算：
                // 删库后自动切到另一个本来就有指纹的库时，开关保持开启，不需要重新验证。
                container.vaultStore.delete(meta)
                container.biometric.clear(meta.id)
                // 云端那份一起删，否则另一台设备下次同步会把本地副本重新传上去，
                // 本地与云端就成了两个互不相干的分支。
                val cloudRemoved = runCatching { deleteCloudCopy(meta.cloudKey, meta.name) }.isSuccess
                val remaining = container.vaultStore.list()
                val next = remaining.firstOrNull()
                val saved = applyFingerprintState(next?.id)
                if (_state.value.activeMeta?.id == meta.id) {
                    active = null
                    masterPassword = null
                    _state.update {
                        it.copy(
                            activeVault = null,
                            // 只剩一个库时直接选中它，避免解锁页停在「选择密码库」却无从选择。
                            activeMeta = next,
                            unlocked = false,
                            screen = Screen.Unlock,
                            vaults = remaining,
                            settings = container.settings.settings,
                            biometricSaved = saved,
                            message = if (cloudRemoved) "密码库已删除，云端副本也已删除" else "密码库已删除"
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            vaults = remaining,
                            settings = container.settings.settings,
                            biometricSaved = saved,
                            message = if (cloudRemoved) "密码库已删除，云端副本也已删除" else "密码库已删除"
                        )
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
                val hadFingerprint = container.settings.settings.fingerprintEnabled
                if (hadFingerprint) {
                    // 修改主密码后旧密文作废：重新加密需要再弹一次指纹，这里直接关闭指纹解锁，
                    // 让用户在设置里重新开启，避免把旧密码留在磁盘上。
                    container.biometric.clear(meta.id)
                    container.settings.settings =
                        container.settings.settings.copy(fingerprintEnabled = false, biometricEnabled = false)
                }
                _state.update {
                    it.copy(
                        message = if (hadFingerprint) "主密码已修改，指纹解锁需要重新开启" else "主密码已修改",
                        settings = container.settings.settings,
                        biometricSaved = container.biometric.has(meta.id)
                    )
                }
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
                val removed = session.vault.purgeItem(itemId)
                if (removed) {
                    session.vault.touch()
                    persist()
                    recomputeSecurity()
                    _state.update { it.copy(message = "已永久删除") }
                    scheduleSync()
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
                val doomed = session.vault.items.filter { it.status == ItemStatus.TRASHED }.map { it.id }
                if (doomed.isEmpty()) return@launch
                // 逐条删除并留墓碑，否则云端会在下一次合并时把它们送回来。
                doomed.forEach { session.vault.purgeItem(it) }
                session.vault.touch()
                persist()
                recomputeSecurity()
                _state.update { it.copy(message = "已清空回收站（${doomed.size} 条）") }
                scheduleSync()
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
        _state.update {
            it.copy(
                activeMeta = meta,
                unlocked = false,
                activeVault = null,
                screen = Screen.Unlock,
                biometricSaved = applyFingerprintState(meta.id),
                settings = container.settings.settings
            )
        }
    }


    // ---------------------------------------------------------------- filters

    fun setSearch(value: String) = _state.update { it.copy(search = value) }
    /**
     * 切换排序：换成别的排序方式时用该方式的默认方向（标题升序，时间降序）；
     * 再次点击同一种方式则反转方向。
     */
    fun setSortMode(value: SortMode) = _state.update { state ->
        if (state.sortMode == value) {
            state.copy(sortAscending = !state.sortAscending)
        } else {
            state.copy(sortMode = value, sortAscending = value == SortMode.TITLE)
        }
    }
    fun setFilterLabel(id: String?) = _state.update { it.copy(filterLabelId = id) }
    fun setThemeMode(mode: String) = updateSettings { it.copy(themeMode = mode) }

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

    /** 修改自动拉取间隔（秒，0=关闭）。改完立即重建轮询任务。 */
    fun setPullIntervalSeconds(value: Int) {
        updateSettings { it.copy(pullIntervalSeconds = value) }
        restartPullLoop()
        if (value > 0) checkRemoteIfDue(force = true)
    }
    fun setScreenshotAllowed(value: Boolean) = updateSettings { it.copy(screenshotAllowed = value) }
    fun setHibpApiKey(value: String) = updateSettings { it.copy(hibpApiKey = value.trim()) }

    private fun updateSettings(transform: (AppSettings) -> AppSettings) {
        val updated = transform(container.settings.settings)
        container.settings.settings = updated
        _state.update { it.copy(settings = updated) }
    }

    /** 开启指纹前先准备一个加密 Cipher，随后由 BiometricPrompt 授权它。 */
    fun prepareFingerprintEnrollment(): Cipher? {
        _state.value.activeMeta ?: return null
        return container.biometric.prepareEncrypt()
    }

    /**
     * 开启指纹解锁。必须传入已通过 BiometricPrompt 认证的 Cipher：
     * 主密码会用它（Keystore 里“每次使用都要认证”的密钥）加密后再落盘。
     */
    fun enableFingerprint(cipher: Cipher?) {
        val password = masterPassword ?: run {
            fail("请先解锁密码库")
            return
        }
        val meta = _state.value.activeMeta ?: return
        if (cipher == null) {
            fail("指纹验证未完成，无法开启指纹解锁")
            return
        }
        if (!container.biometric.persist(meta.id, password, cipher)) {
            fail("系统安全存储不可用，已拒绝以明文保存主密码；请检查设备锁屏设置后重试")
            return
        }
        updateSettings { it.copy(fingerprintEnabled = true, biometricEnabled = true) }
        _state.update {
            it.copy(biometricSaved = true, message = "已开启指纹解锁")
        }
    }

    /** 准备用于解锁的解密 Cipher；密钥被指纹变更作废时返回 null，需要重新开启。 */
    fun prepareBiometricUnlock(): Cipher? {
        val meta = _state.value.activeMeta ?: return null
        return container.biometric.prepareDecrypt(meta.id)
    }

    /** 只有拿到认证通过的 Cipher 才能走到这里，主密码由 Keystore 密钥解出。 */
    fun unlockWithBiometricCipher(cipher: Cipher?) {
        val meta = _state.value.activeMeta ?: run {
            fail("没有可解锁的密码库")
            return
        }
        if (!_state.value.settings.fingerprintEnabled) {
            fail("尚未开启指纹解锁")
            return
        }
        val unlocked = cipher?.let { container.biometric.decrypt(meta.id, it) }
        if (unlocked.isNullOrBlank()) {
            container.biometric.clear(meta.id)
            updateSettings { it.copy(fingerprintEnabled = false, biometricEnabled = false) }
            _state.update { it.copy(biometricSaved = false) }
            fail("指纹凭据已失效（可能是指纹被重新录入），请用主密码解锁后重新开启")
            return
        }
        unlock(meta.id, unlocked)
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
        // WebDAV 密码属于敏感凭据：系统加密存储不可用时拒绝保存，避免明文落盘。
        if (config.password.isNotBlank() && !container.settings.encrypted) {
            fail("系统加密存储不可用，无法安全保存 WebDAV 密码；请先恢复设备锁屏后再试")
            return
        }
        container.settings.webDav = config
        _state.update { it.copy(webDav = config) }
        // 服务器或目录变了，之前的远端时间戳不再有意义。
        lastRemoteMtime = 0
        restartPullLoop()
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
                val result = SyncEngine(client, config)
                    .pullRemote(session, password.toCharArray())
                active = result.container
                val updatedMeta = meta.copy(
                    lastSyncedAt = now(),
                    name = result.container.vault.name,
                    cloudKey = result.container.vault.cloudKey.ifBlank { meta.cloudKey }
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

    /** 重建自动拉取定时器；间隔为 0、未解锁或未配置 WebDAV 时只停掉定时器。 */
    private fun restartPullLoop() {
        pullJob?.cancel()
        pullJob = null
        if (!_state.value.unlocked) return
        if (!_state.value.webDav.isConfigured()) return
        val seconds = _state.value.settings.pullIntervalSeconds
        if (seconds <= 0) return
        pullJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(seconds * 1_000L)
                if (!_state.value.unlocked) break
                runCatching { pullIfRemoteChanged() }
            }
        }
    }

    /**
     * 解锁成功与回到前台时各检查一次云端。
     * force=false 且距上次检查不足一个间隔时直接跳过，避免频繁请求
     * （坚果云免费版限制 600 次请求 / 30 分钟）。
     */
    private fun checkRemoteIfDue(force: Boolean = false) {
        if (!_state.value.unlocked) return
        if (!_state.value.webDav.isConfigured()) return
        val seconds = _state.value.settings.pullIntervalSeconds
        if (seconds <= 0) return
        if (!force && now() - lastPullCheckAt < seconds * 1_000L) return
        lastPullCheckAt = now()
        viewModelScope.launch(Dispatchers.IO) { runCatching { pullIfRemoteChanged() } }
    }

    /** 云端没有变化时只花一次 HEAD 请求；确认变化后才做整库同步。 */
    private suspend fun pullIfRemoteChanged() {
        val session = active ?: return
        val meta = _state.value.activeMeta ?: return
        val config = _state.value.webDav
        if (!config.isConfigured()) return
        if (_state.value.busy || _state.value.syncConflict) return
        val remoteKey = CloudManifest.keyForContent(session.vault.contentKey)
            ?: session.vault.cloudKey
        val remotePath = config.remotePath(remoteKey)
        val client = WebDavClient(config, OkHttp.create())
        try {
            val stat = client.stat(remotePath) ?: client.stat(config.legacyRemotePath())
            if (stat == null) {
                // 云端文件不在了。与桌面端同规则：只要曾同步过就算「可能被删」，
                // 交给 doSync 去认领改名后的键 / 判定删除，不用另外维护墓碑。
                if (session.vault.lastSyncedAt > 0) doSync(silent = true)
                return
            }
            val changed = when {
                // 服务器没给 Last-Modified，只能保守地认为有变化。
                stat.lastModified <= 0 -> true
                // 首次检查：比本机上次同步时间新才算有更新。
                lastRemoteMtime <= 0 -> stat.lastModified > session.vault.lastSyncedAt
                else -> stat.lastModified != lastRemoteMtime
            }
            lastRemoteMtime = stat.lastModified
            if (!changed) return
            doSync(silent = true)
            // 同步后服务器时间会变，重新记录，避免下一次把自己刚上传的当成远端更新。
            lastRemoteMtime = runCatching { client.stat(remotePath)?.lastModified ?: 0L }
                .getOrDefault(lastRemoteMtime)
        } finally {
            client.close()
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
            val result = SyncEngine(client, config)
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
            if (result.direction == SyncDirection.REMOTE_DELETED) {
                // 另一端删除了这个库：先别自己删，交给用户决定是本机也跟着删，
                // 还是把本地这份重新传回云端（自动删掉可能把用户还没备份的数据一起弄没）。
                _state.update {
                    it.copy(
                        busy = false,
                        syncStatus = result.message,
                        syncStatusAt = now(),
                        remoteDeletedName = meta.name
                    )
                }
                return
            }
            active = result.container
            val updatedMeta = meta.copy(lastSyncedAt = now(), name = result.container.vault.name, cloudKey = result.container.vault.cloudKey.ifBlank { meta.cloudKey })
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
        } catch (e: CancellationException) {
            // 调度去重会取消进行中的同步（如连点收藏），取消不是失败，交还给协程机制。
            throw e
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

    /** 备份目录由用户指定；没选过就先让界面弹出选择器。 */
    fun backupNow() {
        val session = active ?: return
        val meta = _state.value.activeMeta ?: return
        val tree = container.settings.settings.backupTreeUri
        if (tree.isBlank()) {
            fail("请先在下方选择备份存储位置")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = session.toBytes()
                val entry = container.backupStore.backup(tree, bytes, session.vault.name)
                    ?: error("无法写入所选目录，请重新选择备份位置")
                container.backupStore.prune(tree, 10)
                val updated = container.settings.settings.copy(lastBackupAt = now())
                container.settings.settings = updated
                _state.update {
                    it.copy(
                        settings = updated,
                        backups = container.backupStore.list(tree),
                        message = "已备份到 ${entry.name}"
                    )
                }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun refreshBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            val tree = container.settings.settings.backupTreeUri
            _state.update { it.copy(backups = container.backupStore.list(tree)) }
        }
    }

    /** 备份目录的显示名（取不到目录名时退回全路径）。 */
    fun backupFolderName(): String {
        val tree = container.settings.settings.backupTreeUri
        if (tree.isBlank()) return "未选择"
        return container.backupStore.folderName(tree).ifBlank { tree }
    }

    /** 记住用户选择的备份目录（SAF 授权持久化）。 */    fun setBackupTree(uri: Uri) {
        container.backupStore.takePermission(uri)
        val updated = container.settings.settings.copy(backupTreeUri = uri.toString())
        container.settings.settings = updated
        _state.update {
            it.copy(
                settings = updated,
                backups = container.backupStore.list(uri.toString()),
                message = "备份位置已更新"
            )
        }
    }

    fun restoreBackup(info: BackupEntry) {
        val meta = _state.value.activeMeta ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = container.backupStore.read(info.uri) ?: error("无法读取该备份文件")
                AtomicFiles.writeAtomic(container.vaultStore.vaultFile(meta), bytes)
                active = null
                masterPassword = null
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

    fun deleteBackup(info: BackupEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                container.backupStore.delete(info.uri)
                val tree = container.settings.settings.backupTreeUri
                _state.update {
                    it.copy(backups = container.backupStore.list(tree), message = "已删除备份")
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

    fun exportVaultXml(uri: Uri) {
        val session = active ?: return
        writeUri(uri, VaultXml.export(session.vault).toByteArray(Charsets.UTF_8))
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
                val failures = mutableListOf<String>()
                uris.forEach { uri ->
                    val label = queryDisplayName(uri) ?: "所选文件"
                    val text = runCatching {
                        app.contentResolver.openInputStream(uri)?.use { input ->
                            input.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        } ?: error("无法读取")
                    }.getOrElse {
                        failures += "$label：无法读取该文件"
                        return@forEach
                    }
                    when {
                        VaultXml.looksLikeXml(text) -> {
                            val cards = runCatching { VaultXml.parse(text) }.getOrNull()
                            if (cards == null) {
                                failures += "$label：XML 格式不正确，仅支持本应用兼容的 XML"
                                return@forEach
                            }
                            if (cards.isEmpty()) {
                                failures += "$label：XML 里没有可导入的条目"
                                return@forEach
                            }
                            val result = mergeXmlCards(session.vault, cards)
                            importedCount += result.first
                            importedLabels += result.second
                        }

                        text.trimStart().startsWith("{") || text.trimStart().startsWith("[") -> {
                            val importedVault = runCatching { VaultCodec.fromJson(text) }.getOrNull()
                            if (importedVault == null) {
                                failures += "$label：JSON 格式不正确，应为本应用导出的 JSON"
                                return@forEach
                            }
                            val items = importedVault.items.map { item ->
                                item.copy(
                                    id = newId(),
                                    fields = item.fields.map { it.copy(id = newId()) }.toMutableList(),
                                )
                            }
                            if (items.isEmpty()) {
                                failures += "$label：JSON 里没有可导入的条目"
                                return@forEach
                            }
                            session.vault.items.addAll(items)
                            importedCount += items.size
                            importedVault.labels.forEach { importedLabel ->
                                if (session.vault.labels.none { existing -> existing.name == importedLabel.name }) {
                                    session.vault.labels.add(importedLabel.copy(id = newId()))
                                    importedLabels++
                                }
                            }
                        }

                        else -> {
                            val items = runCatching { VaultCodec.itemsFromCsv(text) }.getOrDefault(emptyList())
                            if (items.isEmpty()) {
                                failures += "$label：无法识别，支持本应用导出的 JSON / CSV，或兼容格式的 XML"
                                return@forEach
                            }
                            session.vault.items.addAll(items)
                            importedCount += items.size
                        }
                    }
                }

                if (importedCount == 0) {
                    error(failures.firstOrNull() ?: "没有识别到可导入的条目")
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
                            if (failures.isNotEmpty()) append("，${failures.size} 个文件未导入")
                        }
                    )
                }
                scheduleSync()
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        app.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()

    /** 从所选文件的显示名推一个默认库名，省得用户每次手打。 */
    fun suggestVaultName(uri: Uri): String {
        val raw = queryDisplayName(uri)?.substringBeforeLast('.')?.trim().orEmpty()
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().take(40)
        return cleaned.ifBlank { "导入的密码库" }
    }

    fun importEncrypted(uri: Uri, password: String, name: String) {
        // 库名可选：留空就用文件名推一个，用户不用为了能点「导入」先编个名字。
        val trimmed = name.trim().ifBlank { suggestVaultName(uri) }
        if (trimmed.isEmpty()) {
            fail("请输入密码库名称")
            return
        }
        if (trimmed.length > 40) {
            fail("密码库名称不能超过 40 个字符")
            return
        }
        // 导入是「新增一个库」，不是覆盖当前库；同名会让两个库难以区分，直接拒绝。
        if (_state.value.vaults.any { it.name.equals(trimmed, ignoreCase = true) }) {
            fail("本地已有名为「$trimmed」的密码库，请换个名称（导入不会覆盖原库）")
            return
        }
        if (password.length < 8) {
            fail("请输入该密码库的主密码（至少 8 位）")
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: error("无法读取文件，请重新选择")
                val (opened, meta) = try {
                    container.vaultStore.importEncrypted(bytes, password.toCharArray(), trimmed)
                } catch (e: WrongPasswordException) {
                    throw IllegalStateException("主密码不正确，或该文件不是本应用导出的加密密码库")
                } catch (e: Exception) {
                    throw IllegalStateException("无法读取该密码库文件，请确认是本应用导出的 .spv 文件")
                }
                container.settings.lastVaultId = meta.id
                attach(opened, meta, password)
                // 新库一开始没有指纹凭据，开关先归零；下面会提示用户是否开启。
                val saved = applyFingerprintState(meta.id)
                _state.update {
                    it.copy(
                        vaults = container.vaultStore.list(),
                        activeVault = opened.vault,
                        activeMeta = meta,
                        unlocked = true,
                        screen = Screen.Vault,
                        busy = false,
                        security = SecurityAnalyzer.analyze(opened.vault),
                        fingerprintPrompt = it.fingerprintAvailable && !saved,
                        biometricSaved = saved,
                        message = "已导入密码库「${meta.name}」"
                    )
                }
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    fun loadCloudVaults(config: WebDavConfig) {
        if (!config.isConfigured()) {
            fail("请先填写 WebDAV 服务器地址")
            return
        }
        container.settings.webDav = config
        _state.update {
            it.copy(webDav = config, cloudVaultsLoading = true, error = null, syncStatus = "正在读取云端密码库…")
        }
        viewModelScope.launch(Dispatchers.IO) {
            val client = WebDavClient(config, OkHttp.create())
            try {
                client.test()
                val manifest = CloudManifest.parse(client.get(config.manifestPath())?.toString(Charsets.UTF_8))
                val result = when {
                    manifest.vaults.isNotEmpty() -> manifest.vaults.map { entry ->
                        entry.copy(name = if (entry.name.isBlank()) "未命名密码库" else entry.name)
                    }
                    // 没有清单时先看根目录的单文件，再看按库标识命名的散文件。
                    client.get(config.legacyRemotePath()) != null ->
                        listOf(CloudVaultEntry(key = "", name = "云端密码库"))
                    else -> client.list(config.normalizedDir())
                        .map { it.name }
                        .filter { it.startsWith("SafePassword-") && it.endsWith(".spv") }
                        .map { name ->
                            val key = name.removePrefix("SafePassword-").removeSuffix(".spv")
                            CloudVaultEntry(key = key, name = "云端密码库（$key）")
                        }
                }
                _state.update {
                    it.copy(
                        cloudVaults = result,
                        cloudVaultsLoading = false,
                        syncStatus = if (result.isEmpty()) "云端还没有密码库" else "",
                        message = if (result.isEmpty()) "云端还没有密码库" else null,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(cloudVaultsLoading = false, syncStatus = "") }
                fail(e.message ?: "读取云端密码库失败")
            } finally {
                client.close()
            }
        }
    }

    /**
     * 从云端恢复指定密码库。
     *
     * @param key 云端清单里的键；空字符串表示根目录的单文件。
     * @param name 恢复后的本地库名（默认沿用云端清单里的名字）。
     */
    fun restoreFromCloud(config: WebDavConfig, key: String, name: String, password: String) {
        val trimmedName = name.trim().ifBlank { "我的密码库" }
        if (password.length < 8) {
            fail("请输入该密码库的主密码")
            return
        }
        if (!config.isConfigured()) {
            fail("请先填写 WebDAV 服务器地址")
            return
        }
        if (_state.value.vaults.any { it.name.equals(trimmedName, ignoreCase = true) }) {
            fail("本地已存在同名密码库「$trimmedName」，请先改名或删除后再恢复")
            return
        }
        container.settings.webDav = config
        _state.update { it.copy(webDav = config, busy = true, error = null, syncStatus = "正在连接云…") }
        viewModelScope.launch(Dispatchers.IO) {
            val client = WebDavClient(config, OkHttp.create())
            try {
                client.test()
                _state.update { it.copy(syncStatus = "云连接成功，正在下载…") }
                val path = if (key.isBlank()) config.legacyRemotePath() else config.remotePath(key)
                val bytes = client.get(path) ?: error("云端没有找到这个密码库的文件，可能已被删除")
                val (opened, meta) = container.vaultStore.importEncrypted(bytes, password.toCharArray(), trimmedName)
                // 记住这份数据在云端用的是哪个键，之后同步继续读写同一个文件，
                // 而不是按新库名另建一份。
                if (key.isNotBlank() && opened.vault.cloudKey.isBlank()) {
                    opened.vault.cloudKey = key
                    container.vaultStore.save(opened, meta)
                }
                container.settings.lastVaultId = meta.id
                attach(opened, meta, password)
                // 新库一开始没有指纹凭据，开关先归零；下面会提示用户是否开启。
                val saved = applyFingerprintState(meta.id)
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
                        fingerprintPrompt = it.fingerprintAvailable && !saved,
                        biometricSaved = saved,
                        message = "已从云端恢复「${meta.name}」"
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
                fail(networkErrorText(e, "检查失败，请稍后重试"))
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
        // 正在下载或等待安装时再查会重复弹“新版本”，而且此时点“更新”也没有响应。
        // 与其重查一遍，不如直接告诉用户当前处于什么阶段。
        _state.value.updateDownloading.let { downloading ->
            if (downloading) {
                val progress = _state.value.updateProgress
                showMessage(
                    if (progress in 0..99) "正在下载更新：$progress%"
                    else "正在下载更新，请稍候"
                )
                return
            }
        }
        if (_state.value.updateReadyFile != null) {
            showMessage("新版本已下载完成，请点“安装更新”")
            return
        }
        _state.update { it.copy(updateChecking = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val releases = GitHubUpdater.fetchReleases()
                val notes = GitHubUpdater.releaseNotes(releases, limit = MAX_CHANGELOG_VERSIONS)
                val published = releases.filter { !it.draft && !it.prerelease && it.tagName.isNotBlank() }
                val latest = published.firstOrNull { release ->
                    release.assets.any { asset -> asset.name.endsWith(".apk", ignoreCase = true) }
                } ?: published.firstOrNull()
                val current = BuildConfig.VERSION_NAME
                if (latest == null) {
                    _state.update { it.copy(changelog = notes) }
                    showMessage("未找到可用的发布版本")
                    return@launch
                }
                val info = GitHubUpdater.toUpdateInfo(latest)
                val newer = GitHubUpdater.isNewer(info.version, current) && info.apkUrl.isNotBlank()
                _state.update {
                    it.copy(
                        changelog = notes,
                        updateInfo = info.takeIf { newer },
                        updateDialogVisible = newer && showDialog
                    )
                }
                if (newer) {
                    // 用户主动点检查才弹更新框；后台检查只留 updateInfo，由「检查更新」那一行
                    // 的副标题提示。否则后台检查会在用户没操作时弹框，多数时候正好落在解锁界面上。
                    if (showDialog) return@launch
                    showMessage("发现新版本 v${info.version}")
                } else {
                    showMessage("当前已是最新版本 v$current")
                }
            } catch (e: Exception) {
                // 网络失败、超时、GitHub 限流或返回异常内容都归到这一类，只给用户可读提示。
                android.util.Log.w("AppViewModel", "检查更新失败", e)
                showMessage("检查更新失败，请检查网络后重试")
            } finally {
                _state.update { it.copy(updateChecking = false) }
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
                target.delete()
                android.util.Log.w("AppViewModel", "下载更新失败", e)
                _state.update {
                    it.copy(
                        updateDownloading = false,
                        updateProgress = -1,
                        updateReadyFile = null
                    )
                }
                showMessage("下载失败，请检查网络后重试")
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

    /** 通知栏点开后安装没能直接完成时，回到应用内给出可重试的安装入口。 */
    fun offerInstall(path: String) = _state.update { it.copy(updateReadyFile = path) }

    fun openChangelog() {
        // 更新日志随包内置，离线也能查看，内容只包含功能介绍与问题修复。
        val local = UpdateLog.load(getApplication())
        _state.update {
            it.copy(changelog = local.ifEmpty { it.changelog }, showChangelog = true)
        }
        // 云端最多 50 个版本，拉到后合并：按版本号倒序，未发版的本地条目自然排在最前。
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val remote = GitHubUpdater.releaseNotes(GitHubUpdater.fetchReleases(), limit = MAX_CHANGELOG_VERSIONS)
                if (remote.isEmpty()) return@runCatching
                val known = remote.map { it.version }.toSet()
                val localOnly = local.filter { it.version.isNotBlank() && it.version !in known }
                val merged = (localOnly + remote).sortedByDescending { versionRank(it.version) }
                _state.update { if (it.showChangelog) it.copy(changelog = merged) else it }
            }
        }
    }

    fun dismissChangelog() = _state.update { it.copy(showChangelog = false) }

    fun dismissFingerprintPrompt() = _state.update { it.copy(fingerprintPrompt = false) }

    /** 删除云端的某个密码库文件，并把清单里的条目一并去掉。 */
    /**
     * 删除云端那份密码库文件并把它从清单里摘掉。
     *
     * 只在本地删库时调用：本地和云端必须一起消失，否则另一台设备会把
     * 本地副本当成「云端还没有」重新上传，两边就分叉了。
     */
    private suspend fun deleteCloudCopy(cloudKey: String, name: String) {
        val config = _state.value.webDav
        if (!config.isConfigured()) return
        val client = WebDavClient(config, OkHttp.create())
        try {
            client.test()
            val manifest = CloudManifest.parse(
                client.get(config.manifestPath())?.toString(Charsets.UTF_8)
            )
            // 老库可能还没有键：按名字去清单里找它实际用的键，找不到就什么也不用删。
            val key = cloudKey.ifBlank { manifestEntryKey(manifest, name) }
            if (key != null) {
                client.delete(config.remotePath(key))
            } else if (cloudKey.isBlank()) {
                client.delete(config.legacyRemotePath())
            }
            // 与桌面端一致：删文件 + 从清单摘掉条目（不做墓碑）。
            // 另一台设备据此得到「清单里没有、云端也没有」→ 判为已删除并弹窗询问。
            val victim = if (key != null) manifest.vaults.firstOrNull { it.key == key } else null
            if (victim != null) {
                manifest.remove(victim.key)
                client.put(
                    config.manifestPath(),
                    manifest.toJson().toByteArray(Charsets.UTF_8)
                )
            }
        } finally {
            client.close()
        }
    }

    /** 改名后同步云端清单里的显示名（云端文件名用的是 cloudKey，不会变）。 */
    private suspend fun renameCloudEntry(cloudKey: String, oldName: String, newName: String) {
        val config = _state.value.webDav
        if (!config.isConfigured()) return
        val client = WebDavClient(config, OkHttp.create())
        try {
            client.test()
            val manifest = CloudManifest.parse(
                client.get(config.manifestPath())?.toString(Charsets.UTF_8)
            )
            val key = cloudKey.ifBlank { manifestEntryKey(manifest, oldName) } ?: return
            val entry = manifest.vaults.firstOrNull { it.key == key } ?: return
            if (entry.name == newName) return
            manifest.upsert(key, newName, entry.updatedAt, entry.size)
            client.put(
                config.manifestPath(),
                manifest.toJson().toByteArray(Charsets.UTF_8)
            )
        } finally {
            client.close()
        }
    }

    /** 清单里显示名匹配的那条记录的键；没有就返回 null。 */
    private fun manifestEntryKey(manifest: CloudManifest, name: String): String? {
        val want = name.trim().lowercase()
        return manifest.vaults.firstOrNull { it.name.trim().lowercase() == want }?.key
    }

    /** 用户选择「删除本地」：跟着云端一起删掉，回到解锁或欢迎页。 */
    fun deleteRemoteDeletedVault() {
        val meta = _state.value.activeMeta
        val removedName = _state.value.remoteDeletedName ?: meta?.name ?: "密码库"
        viewModelScope.launch(Dispatchers.IO) {
            if (meta != null) {
                container.vaultStore.delete(meta)
                container.biometric.clear(meta.id)
            }
            val remaining = container.vaultStore.list()
            val next = remaining.firstOrNull()
            if (next == null) {
                container.settings.lastVaultId = ""
            } else if (meta != null && container.settings.lastVaultId == meta.id) {
                container.settings.lastVaultId = next.id
            }
            val saved = applyFingerprintState(next?.id)
            active = null
            masterPassword = null
            _state.update {
                it.copy(
                    activeVault = null,
                    activeMeta = next,
                    unlocked = false,
                    screen = Screen.Unlock,
                    vaults = remaining,
                    settings = container.settings.settings,
                    biometricSaved = saved,
                    busy = false,
                    search = "",
                    onlyFavorites = false,
                    remoteDeletedName = null,
                    message = if (remaining.isEmpty()) {
                        "「$removedName」已删除，本地已无密码库"
                    } else {
                        "「$removedName」已在本地删除"
                    }
                )
            }
        }
    }

    /**
     * 用户选择「恢复到云端」：把本地这份强制推回云端。
     *
     * 与桌面端的 `restore_to_cloud` 一致——不看时间戳、不看云端有没有文件，
     * 直接把本体写上去并刷新清单，所以不会再次被判定成「云端已删除」。
     */
    fun restoreRemoteDeletedToCloud() {
        val session = active ?: return
        val meta = _state.value.activeMeta
        _state.update { it.copy(remoteDeletedName = null) }
        if (meta == null) return
        val password = masterPassword ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val config = _state.value.webDav
            if (!config.isConfigured()) {
                fail("请先配置 WebDAV 服务器")
                return@launch
            }
            val client = WebDavClient(config, OkHttp.create())
            try {
                _state.update { it.copy(busy = true, syncStatus = "正在恢复到云端…", error = null) }
                val engine = SyncEngine(client, config)
                val result = engine.restoreToCloud(session)
                active = result.container
                val updated = meta.copy(
                    lastSyncedAt = now(),
                    name = result.container.vault.name,
                    cloudKey = result.container.vault.cloudKey.ifBlank { meta.cloudKey }
                )
                result.container.saveTo(container.vaultStore.vaultFile(updated))
                container.vaultStore.updateMeta(updated)
                _state.update {
                    it.copy(
                        activeVault = result.container.vault,
                        activeMeta = updated,
                        vaults = container.vaultStore.list(),
                        busy = false,
                        syncStatus = result.message,
                        syncStatusAt = now(),
                        message = result.message,
                        security = SecurityAnalyzer.analyze(result.container.vault, it.breached),
                        dataVersion = it.dataVersion + 1
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(networkErrorText(e, "恢复到云端失败，请稍后重试"))
            } finally {
                client.close()
            }
        }
    }

    fun deleteCloudVault(entry: CloudVaultEntry) {
        val config = _state.value.webDav
        if (!config.isConfigured()) {
            fail("请先配置 WebDAV 服务器")
            return
        }
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val client = WebDavClient(config, OkHttp.create())
            try {
                client.test()
                if (entry.key.isBlank()) {
                    client.delete(config.legacyRemotePath())
                } else {
                    client.delete(config.remotePath(entry.key))
                }
                val manifest = CloudManifest.parse(
                    client.get(config.manifestPath())?.toString(Charsets.UTF_8)
                )
                // 留墓碑而不是直接从清单里抹掉：别的设备靠它知道这份库被删了，
                // 否则它们只会看到文件消失，只能报「云端没有找到」而不知道该跟随删除。
                manifest.remove(entry.key)
                client.put(
                    config.manifestPath(),
                    manifest.toJson().toByteArray(Charsets.UTF_8)
                )
                _state.update {
                    it.copy(
                        busy = false,
                        cloudVaults = it.cloudVaults.filterNot { cloud -> cloud.key == entry.key },
                        message = "已删除云端密码库「${entry.name}」"
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false) }
                fail(e.message ?: "删除云端密码库失败")
            } finally {
                client.close()
            }
        }
    }

    /** 打开 GitHub Releases 页面，用于查看 50 版之前的更新日志。 */
    fun openReleasesPage() {
        runCatching {
            val intent = android.content.Intent(
                android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(GitHubUpdater.RELEASES_PAGE)
            ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            app.startActivity(intent)
        }.onFailure { showMessage("没有可用的浏览器") }
    }

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
        } else {
            // 回到前台时顺带检查一次云端，是否真的拉取由 lastPullCheckAt 控制。
            checkRemoteIfDue()
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
        // 没选备份目录时静默跳过：自动备份不该在没有用户授权的位置乱写文件。
        if (settings.backupTreeUri.isBlank()) return
        if (now() - settings.lastBackupAt < settings.autoBackupDays * 86_400_000L) return
        runCatching {
            container.backupStore.backup(settings.backupTreeUri, session.toBytes(), session.vault.name)
                ?: return@runCatching
            container.backupStore.prune(settings.backupTreeUri, 10)
            val updated = container.settings.settings.copy(lastBackupAt = now())
            container.settings.settings = updated
            _state.update {
                it.copy(settings = updated, backups = container.backupStore.list(updated.backupTreeUri))
            }
        }
    }

    private companion object {
        /** 应用内最多展示 50 个版本的更新日志，更早的去 GitHub Releases 看。 */
        const val MAX_CHANGELOG_VERSIONS = 50
    }

    /**
     * 指纹开关跟着「当前密码库有没有凭据」走。
     *
     * 每个库的指纹凭据互相独立：切到另一个没有凭据的库时开关自动变关，
     * 用主密码解锁后可以再开启；而删库后自动切到的库如果本来就有凭据，开关保持开启。
     */
    private fun applyFingerprintState(metaId: String?): Boolean {
        val saved = metaId != null && container.biometric.has(metaId)
        val settings = container.settings.settings
        if (settings.fingerprintEnabled != saved) {
            container.settings.settings = settings.copy(
                fingerprintEnabled = saved,
                biometricEnabled = saved
            )
        }
        return saved
    }

    /** 把 "1.5.1" 这类版本号折算成可比较的数值，用于日志排序。 */
    private fun versionRank(version: String): Long {
        val parts = version.split('.').mapNotNull { it.toIntOrNull() }
        var rank = 0L
        repeat(4) { index ->
            rank = rank * 1000 + (parts.getOrNull(index) ?: 0).coerceIn(0, 999)
        }
        return rank
    }

    private fun fail(e: Throwable) {
        fail(e.message ?: "操作失败")
    }

    /**
     * 把底层异常翻译成一句人话。
     *
     * 直接把 `e.message` 弹给用户会看到 "java.net.SocketTimeoutException: timeout"
     * 这类信息，普通用户读不懂也做不了什么；网络类异常统一按「超时 / 连不上」两类提示。
     */
    private fun networkErrorText(e: Throwable, fallback: String): String {
        var current: Throwable? = e
        var guard = 0
        while (current != null && guard < 8) {
            val name = current.javaClass.simpleName
            when {
                name.contains("Timeout", ignoreCase = true) ->
                    return "网络连接超时，请检查网络后重试"
                name.contains("UnknownHost", ignoreCase = true) ||
                    name.contains("Connect", ignoreCase = true) ->
                    return "无法连接服务器，请检查网络后重试"
            }
            current = current.cause?.takeUnless { it === current }
            guard++
        }
        return fallback
    }

    private fun fail(message: String) {
        _state.update { it.copy(busy = false, syncStatus = "", error = message) }
    }
}
