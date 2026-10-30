package com.safeincloud.android.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.safeincloud.android.MainActivity
import com.safeincloud.android.security.BiometricHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, state: AppUiState, activity: MainActivity) {
    var showChangePassword by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showAutoLock by remember { mutableStateOf(false) }
    var showBackupInterval by remember { mutableStateOf(false) }
    var showDefaultLabelMenu by remember { mutableStateOf(false) }
    var pendingImportVault by remember { mutableStateOf<android.net.Uri?>(null) }
    val context = LocalContext.current
    val fingerprintStatus = BiometricHelper.status(context)

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
    ) { uri -> if (uri != null) viewModel.exportSafeInCloudXml(uri) }
    val exportCsv = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> if (uri != null) viewModel.exportCsv(uri) }
    val exportVault = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri -> if (uri != null) viewModel.exportEncrypted(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigate(Screen.Vault) }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("设置") },
                actions = {
                    IconButton(onClick = { viewModel.lock() }) {
                        Icon(Icons.Outlined.Lock, contentDescription = "锁定")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
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
                                } else {
                                    BiometricHelper.authenticate(
                                        activity,
                                        onSuccess = {
                                            if (checked) {
                                                viewModel.enableFingerprint()
                                            } else {
                                                viewModel.disableFingerprint()
                                            }
                                        },
                                        onError = { message ->
                                            if (!message.contains("取消")) viewModel.showError(message)
                                        }
                                    )
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
                    title = "管理密码库",
                    subtitle = "新建、切换、重命名或删除",
                    onClick = { viewModel.navigate(Screen.VaultManager) },
                    trailing = { Icon(Icons.Outlined.Folder, contentDescription = null) }
                )
                SettingRow(
                    title = "重命名当前密码库",
                    subtitle = state.activeMeta?.name.orEmpty(),
                    onClick = { showRename = true }
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
                    subtitle = "支持 .json / .csv / .xml / .txt；XML 兼容原版 SafeInCloud 导出",
                    onClick = { importData.launch(arrayOf("*/*")) },
                    trailing = { Icon(Icons.Outlined.Upload, contentDescription = null) }
                )
                SettingRow(
                    title = "导入加密保险库",
                    subtitle = "需要原数据库主密码",
                    onClick = { importVault.launch(arrayOf("application/octet-stream", "*/*")) },
                    trailing = { Icon(Icons.Outlined.Upload, contentDescription = null) }
                )
                SettingRow(
                    title = "导出 JSON",
                    subtitle = "明文，包含全部字段，注意保管",
                    onClick = { exportJson.launch("SafePassword.json") },
                    trailing = { Icon(Icons.Outlined.Download, contentDescription = null) }
                )
                SettingRow(
                    title = "导出 CSV",
                    subtitle = "适合迁移到其他密码管理器",
                    onClick = { exportCsv.launch("SafePassword.csv") },
                    trailing = { Icon(Icons.Outlined.Download, contentDescription = null) }
                )
                SettingRow(
                    title = "导出 XML",
                    subtitle = "可还原到原版 SafeInCloud",
                    onClick = { exportXml.launch("SafePassword.xml") },
                    trailing = { Icon(Icons.Outlined.Download, contentDescription = null) }
                )
                SettingRow(
                    title = "导出加密备份",
                    subtitle = "仅能用主密码打开",
                    onClick = { exportVault.launch("SafePassword.sicv") },
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
                    subtitle = "回到解锁界面",
                    onClick = { viewModel.lock() },
                    trailing = { Icon(Icons.Outlined.Lock, contentDescription = null) }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
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
            onSelect = {
                showBackupInterval = false
                viewModel.setAutoBackupDays(it)
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
            Column {
                PasswordField(oldPassword, { oldPassword = it }, "旧主密码")
                Spacer(Modifier.height(8.dp))
                PasswordField(newPassword, { newPassword = it }, "新主密码（至少 8 位）")
                Spacer(Modifier.height(8.dp))
                PasswordField(confirm, { confirm = it }, "确认新主密码")
                if (mismatch) {
                    Text("两次输入不一致", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
private fun ImportVaultDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("导入的密码库") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入加密保险库") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("新密码库名称") },
                    singleLine = true,
                    colors = safeFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                PasswordField(password, { password = it }, "原数据库主密码")
                Spacer(Modifier.height(8.dp))
                Text(
                    "导入后会用同一个主密码重新加密为新的数据库。附件不会随加密库导入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password, name) },
                enabled = password.isNotBlank() && name.isNotBlank()
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
