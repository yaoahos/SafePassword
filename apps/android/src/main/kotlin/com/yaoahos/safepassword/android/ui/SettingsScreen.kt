package com.yaoahos.safepassword.android.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.yaoahos.safepassword.android.MainActivity
import com.yaoahos.safepassword.android.security.BiometricHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 导出文件默认名：密码库名称-六位日期-六位时间.后缀，与本地备份命名风格一致。 */
private fun defaultExportName(state: AppUiState, extension: String): String {
    val base = (state.activeVault?.name ?: "SafePassword")
        .replace(Regex("[^\\p{L}\\p{N}_-]"), "_").take(40).ifBlank { "SafePassword" }
    val stamp = SimpleDateFormat("yyMMdd-HHmmss", Locale.US).format(Date())
    return "$base-$stamp.$extension"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, state: AppUiState, activity: MainActivity) {
    var showChangePassword by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showAutoLock by remember { mutableStateOf(false) }
    var showBackupInterval by remember { mutableStateOf(false) }
    var showDefaultLabelMenu by remember { mutableStateOf(false) }
    var pendingImportVault by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingBackupDays by remember { mutableStateOf(0) }
    val context = LocalContext.current
    val fingerprintStatus = BiometricHelper.status(context)

    // 进设置页时体检一次指纹：硬件损坏 / 录入被删就自动关掉指纹解锁，
    // 否则会出现「指纹已经坏了却还要验证指纹才能关闭」的死锁。
    LaunchedEffect(Unit) { viewModel.ensureFingerprintHealthy() }

    // 开启自动备份时先让用户选一个目录，备份不再写应用内部存储。
    val backupFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        val days = pendingBackupDays
        pendingBackupDays = 0
        if (uri != null) {
            viewModel.setBackupTree(uri)
            if (days > 0) viewModel.setAutoBackupDays(days)
        }
    }

    val importData = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) viewModel.importFiles(uris)
    }
    val importVault = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingImportVault = uri
    }
    val exportJson = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) viewModel.exportPlain(uri) }
    val exportXml = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/xml")
    ) { uri -> if (uri != null) viewModel.exportVaultXml(uri) }
    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) viewModel.exportCsv(uri) }
    val exportVault = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) viewModel.exportEncrypted(uri) }
    // JSON / CSV / XML 都是明文（含全部密码），导出前先确认一次，避免手滑。
    var pendingPlainExport by remember { mutableStateOf<(() -> Unit)?>(null) }
    // 临时入口：压缩已有图标。用完连同 AppViewModel.compactIcons 一起删掉。
    var showCompactIcons by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigate(Screen.Vault) }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionCard(title = "安全") {
                SettingRow(
                    title = "自动锁定",
                    subtitle = autoLockLabel(state.settings.autoLockSeconds),
                    onClick = { showAutoLock = true }
                )
                SettingRow(
                    title = "指纹解锁",
                    subtitle = if (state.settings.fingerprintEnabled) {
                        "已开启 · 使用系统指纹单独验证"
                    } else if (!fingerprintStatus.available || !fingerprintStatus.enrolled) {
                        "${fingerprintStatus.message}，点击去系统设置"
                    } else {
                        fingerprintStatus.message
                    },
                    onClick = if (!state.settings.fingerprintEnabled &&
                        (!fingerprintStatus.available || !fingerprintStatus.enrolled)
                    ) {
                        { openBiometricSettings(context) }
                    } else {
                        null
                    },
                    trailing = {
                        Switch(
                            checked = state.settings.fingerprintEnabled,
                            enabled = state.settings.fingerprintEnabled ||
                                (fingerprintStatus.available && fingerprintStatus.enrolled),
                            onCheckedChange = { checked ->
                                if (!fingerprintStatus.available || !fingerprintStatus.enrolled) {
                                    if (checked) {
                                        openBiometricSettings(context)
                                    } else {
                                        viewModel.disableFingerprint()
                                    }
                                } else if (!checked) {
                                    // 关闭指纹也要验证一次指纹，避免他人临时借用手机时直接关掉解锁门槛。
                                    val decryptCipher = viewModel.prepareBiometricUnlock()
                                    if (decryptCipher == null) {
                                        // 凭据已失效（指纹被重录等）：验证这一步已经走不通，直接关，不再卡死。
                                        viewModel.disableFingerprint("指纹凭据已失效，已直接关闭指纹解锁")
                                    } else {
                                        BiometricHelper.authenticate(
                                            activity,
                                            decryptCipher,
                                            onSuccess = { viewModel.disableFingerprint() },
                                            onFailure = { code, message ->
                                                when {
                                                    BiometricHelper.isUserCancelled(code) -> Unit
                                                    // 传感器损坏/无法处理/永久锁定：再验证也过不了，直接关。
                                                    BiometricHelper.isHardwareError(code) ->
                                                        viewModel.disableFingerprint("指纹识别不可用，已自动关闭指纹解锁")
                                                    else -> viewModel.showError(message)
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    // 开启指纹需要一把 Keystore 密钥：先认证，拿到授权的 Cipher 再加密主密码。
                                    val cipher = viewModel.prepareFingerprintEnrollment()
                                    if (cipher == null) {
                                        viewModel.showError("系统安全存储不可用，无法开启指纹解锁")
                                    } else {
                                        BiometricHelper.authenticate(
                                            activity,
                                            cipher,
                                            onSuccess = { authenticated ->
                                                viewModel.enableFingerprint(authenticated)
                                            },
                                            onFailure = { code, message ->
                                                if (!BiometricHelper.isUserCancelled(code)) {
                                                    viewModel.showError(message)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        )
                    }
                )
                SettingRow(
                    title = "允许截屏",
                    subtitle = "关闭时使用 FLAG_SECURE 阻止截屏与最近任务预览",
                    trailing = {
                        Switch(
                            checked = state.settings.screenshotAllowed,
                            onCheckedChange = viewModel::setScreenshotAllowed
                        )
                    }
                )
                SettingRow(
                    title = "修改主密码",
                    subtitle = "只重新包裹数据密钥，不重新加密附件",
                    onClick = { showChangePassword = true }
                )
            }

            SectionCard(title = "同步与备份") {
                SettingRow(
                    title = "WebDAV 同步",
                    subtitle = if (state.webDav.isConfigured()) {
                        "已配置 · ${state.webDav.remoteDir}"
                    } else {
                        "未配置 · 支持坚果云、Nextcloud、NAS 等"
                    },
                    onClick = { viewModel.navigate(Screen.WebDav()) },
                    trailing = { Icon(Icons.Outlined.Cloud, contentDescription = null) }
                )
                SettingRow(
                    title = "本地备份",
                    subtitle = "上次备份：${formatDate(state.settings.lastBackupAt)}",
                    onClick = { viewModel.navigate(Screen.Backups) },
                    trailing = { Icon(Icons.Outlined.Backup, contentDescription = null) }
                )
                SettingRow(
                    title = "自动备份间隔",
                    subtitle = if (state.settings.autoBackupDays <= 0) "已关闭" else "每 ${state.settings.autoBackupDays} 天",
                    onClick = { showBackupInterval = true }
                )
                SettingRow(
                    title = "立即同步",
                    subtitle = "上次同步：${formatDate(state.activeMeta?.lastSyncedAt ?: 0)}",
                    onClick = { viewModel.syncNow() }
                )
            }

            SectionCard(title = "密码库") {
                SettingRow(
                    title = "密码库管理",
                    subtitle = "新建、切换、重命名或删除",
                    onClick = { viewModel.navigate(Screen.VaultManager) },
                    trailing = { Icon(Icons.Outlined.Folder, contentDescription = null) }
                )
                SettingRow(
                    title = "重命名当前密码库",
                    subtitle = state.activeMeta?.name.orEmpty(),
                    onClick = { showRename = true }
                )
                // 临时入口：一次性把老图标压成新规格，用完可删。
                SettingRow(
                    title = "压缩已有图标",
                    subtitle = "按新规格重新编码当前密码库的图标",
                    onClick = { showCompactIcons = true }
                )
            }

            SectionCard(title = "显示与搜索") {
                val labels = state.activeVault?.labels.orEmpty()
                    .sortedWith(compareBy({ it.sortOrder }, { it.name }))
                val defaultName = labels.firstOrNull { it.id == state.settings.defaultLabelId }?.name
                    ?: "全部条目"
                Box {
                    SettingRow(
                        title = "默认展示",
                        subtitle = defaultName,
                        onClick = { showDefaultLabelMenu = true }
                    )
                    DropdownMenu(
                        expanded = showDefaultLabelMenu,
                        onDismissRequest = { showDefaultLabelMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部条目") },
                            onClick = {
                                showDefaultLabelMenu = false
                                viewModel.setDefaultLabel(null)
                            }
                        )
                        labels.forEach { label ->
                            DropdownMenuItem(
                                text = { Text(label.name) },
                                onClick = {
                                    showDefaultLabelMenu = false
                                    viewModel.setDefaultLabel(label.id)
                                }
                            )
                        }
                    }
                }
                SettingRow(
                    title = "搜索密码",
                    subtitle = "允许搜索框匹配密码字段内容",
                    trailing = {
                        Switch(
                            checked = state.settings.searchPasswords,
                            onCheckedChange = viewModel::setSearchPasswords
                        )
                    }
                )
            }

            SectionCard(title = "导入与导出") {
                SettingRow(
                    title = "导入 JSON / CSV / XML",
                    subtitle = "支持 .json / .csv / .xml / .txt；XML 兼容原版密码库导出",
                    onClick = { importData.launch(arrayOf("*/*")) },
                    trailing = { Icon(Icons.Outlined.Upload, contentDescription = null) }
                )
                SettingRow(
                    title = "导入加密保险库",
                    subtitle = "新增一个密码库，不覆盖现有的；需要原数据库主密码",
                    onClick = { importVault.launch(arrayOf("application/octet-stream", "*/*")) },
                    trailing = { Icon(Icons.Outlined.Upload, contentDescription = null) }
                )
                SettingRow(
                    title = "导出 JSON",
                    subtitle = "明文，包含全部字段，注意保管",
                    onClick = { pendingPlainExport = { exportJson.launch(defaultExportName(state, "json")) } },
                    trailing = { Icon(Icons.Outlined.Download, contentDescription = null) }
                )
                SettingRow(
                    title = "导出 CSV",
                    subtitle = "适合迁移到其他密码管理器",
                    onClick = { pendingPlainExport = { exportCsv.launch(defaultExportName(state, "csv")) } },
                    trailing = { Icon(Icons.Outlined.Download, contentDescription = null) }
                )
                SettingRow(
                    title = "导出 XML",
                    subtitle = "可在支持该格式的应用中打开",
                    onClick = { pendingPlainExport = { exportXml.launch(defaultExportName(state, "xml")) } },
                    trailing = { Icon(Icons.Outlined.Download, contentDescription = null) }
                )
                SettingRow(
                    title = "导出加密备份",
                    subtitle = "仅能用主密码打开",
                    onClick = { exportVault.launch(defaultExportName(state, "spv")) },
                    trailing = { Icon(Icons.Outlined.Download, contentDescription = null) }
                )
            }

            SectionCard(title = "其他") {
                SettingRow(
                    title = "关于",
                    subtitle = "版本、技术栈与功能范围",
                    onClick = { viewModel.navigate(Screen.About) },
                    trailing = { Icon(Icons.Outlined.Info, contentDescription = null) }
                )
                SettingRow(
                    title = "锁定并清除内存中的主密码",
                    // 按实际观感写：锁定后多数机型会先退回桌面，再回来才是解锁界面。
                    subtitle = "回到桌面，需重新解锁",
                    onClick = { viewModel.lock() },
                    trailing = { Icon(Icons.Outlined.Lock, contentDescription = null) }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    pendingPlainExport?.let { action ->
        ConfirmDialog(
            title = "导出为明文文件？",
            text = "JSON / CSV / XML 里是完整的明文密码与备注，任何拿到文件的人都能直接看到。" +
                "建议只在必须迁移时导出，用完及时删除。",
            confirmText = "仍要导出",
            destructive = true,
            onDismiss = { pendingPlainExport = null },
            onConfirm = {
                pendingPlainExport = null
                action()
            }
        )
    }

    // 临时入口：压缩已有图标。
    if (showCompactIcons) {
        ConfirmDialog(
            title = "压缩已有图标？",
            text = "会把当前密码库里所有自定义图标按新规格（正方形 192px）重新编码，" +
                "尺寸和体积都会变小，画质无损。条目的修改时间会更新，并触发一次同步。\n\n" +
                "只处理当前密码库；其它密码库切换过去再点一次即可。",
            confirmText = "开始压缩",
            onDismiss = { showCompactIcons = false },
            onConfirm = {
                showCompactIcons = false
                viewModel.compactIcons()
            }
        )
    }

    if (showAutoLock) {
        OptionDialog(
            title = "自动锁定",
            options = listOf(
                -1 to "从不",
                0 to "立即",
                30 to "30 秒",
                60 to "1 分钟",
                300 to "5 分钟",
                900 to "15 分钟"
            ),
            selected = state.settings.autoLockSeconds,
            onDismiss = { showAutoLock = false },
            onSelect = {
                showAutoLock = false
                viewModel.setAutoLockSeconds(it)
            }
        )
    }

    if (showBackupInterval) {
        OptionDialog(
            title = "自动备份间隔",
            options = listOf(0 to "关闭", 1 to "每天", 3 to "每 3 天", 7 to "每 7 天", 30 to "每 30 天"),
            selected = state.settings.autoBackupDays,
            onDismiss = { showBackupInterval = false },
            onSelect = { days ->
                showBackupInterval = false
                if (days > 0 && state.settings.backupTreeUri.isBlank()) {
                    // 开启自动备份前必须先让用户指定备份目录。
                    pendingBackupDays = days
                    backupFolderPicker.launch(null)
                } else {
                    viewModel.setAutoBackupDays(days)
                }
            }
        )
    }

    if (showRename) {
        TextInputDialog(
            title = "重命名密码库",
            label = "名称",
            initialValue = state.activeMeta?.name.orEmpty(),
            onDismiss = { showRename = false },
            onConfirm = {
                showRename = false
                viewModel.renameVault(it)
            }
        )
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            onDismiss = { showChangePassword = false },
            onConfirm = { old, new ->
                showChangePassword = false
                viewModel.changePassword(old, new)
            }
        )
    }

    pendingImportVault?.let { uri ->
        ImportVaultDialog(
            initialName = viewModel.suggestVaultName(uri),
            onDismiss = { pendingImportVault = null },
            onConfirm = { password, name ->
                pendingImportVault = null
                viewModel.importEncrypted(uri, password, name)
            }
        )
    }
}

@Composable
private fun OptionDialog(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    TextButton(
                        onClick = { onSelect(value) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(label, modifier = Modifier.weight(1f))
                            if (value == selected) Text("✓")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = confirm.isNotBlank() && newPassword != confirm
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改主密码") },
        text = {
            Column(Modifier.imePadding()) {
                PasswordField(oldPassword, { oldPassword = it }, "旧主密码")
                Spacer(Modifier.height(8.dp))
                PasswordField(newPassword, { newPassword = it }, "新主密码（至少 8 位）")
                Spacer(Modifier.height(8.dp))
                PasswordField(confirm, { confirm = it }, "确认新主密码")
                if (mismatch) {
                    Spacer(Modifier.height(6.dp))
                    InlineNotice(
                        text = "两次输入不一致",
                        accent = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(oldPassword, newPassword) },
                enabled = oldPassword.isNotBlank() && newPassword.length >= 8 && newPassword == confirm
            ) { Text("修改") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ImportVaultDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入加密保险库") },
        text = {
            Column(Modifier.imePadding()) {
                PasswordField(password, { password = it }, "原数据库主密码")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("新密码库名称（可留空）") },
                    singleLine = true,
                    colors = safeFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "导入会新增一个密码库，不会覆盖现有密码库；留空则按文件名命名，导入后也可以改名。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password, name) },
                enabled = password.isNotBlank()
            ) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun openBiometricSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_BIOMETRIC_ENROLL)
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            runCatching { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
        }
}

private fun autoLockLabel(seconds: Int): String = when (seconds) {
    -1 -> "从不"
    0 -> "切到后台立即锁定"
    in 1..59 -> "$seconds 秒"
    in 60..3599 -> "${seconds / 60} 分钟"
    else -> "${seconds / 3600} 小时"
}
