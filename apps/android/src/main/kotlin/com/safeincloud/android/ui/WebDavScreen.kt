package com.safeincloud.android.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.safeincloud.core.sync.WebDavConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavScreen(viewModel: AppViewModel, state: AppUiState, restore: Boolean = false) {
    var baseUrl by remember { mutableStateOf(state.webDav.baseUrl) }
    var username by remember { mutableStateOf(state.webDav.username) }
    var password by remember { mutableStateOf(state.webDav.password) }
    var remoteDir by remember { mutableStateOf(state.webDav.remoteDir) }
    var autoSync by remember { mutableStateOf(state.settings.autoSync) }
    var showMasterPrompt by remember { mutableStateOf(false) }
    var showPullConfirm by remember { mutableStateOf(false) }
    var masterPassword by remember { mutableStateOf("") }
    var masterVisible by remember { mutableStateOf(false) }
    var masterError by remember { mutableStateOf(false) }

    fun currentConfig() = WebDavConfig(
        baseUrl = baseUrl.trim(),
        username = username.trim(),
        password = password,
        remoteDir = remoteDir.trim().ifBlank { "SafePassword" },
        enabled = true
    )

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.goBack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text(if (restore) "从云端恢复" else "WebDAV 同步") },
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
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            if (restore) "从云端取回密码库" else "只同步加密文件",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (restore) {
                                "填写 WebDAV 信息后点“保存并立即同步”，再输入主密码，应用会下载云端密码库到本地。"
                            } else {
                                "WebDAV 服务器只看到密文；主密码和明文不会上传。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                label = { Text("服务器地址") },
                placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                singleLine = true,
                colors = safeFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("用户名") },
                singleLine = true,
                colors = safeFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = "密码 / 应用密码"
            )
            OutlinedTextField(
                value = remoteDir,
                onValueChange = { remoteDir = it },
                label = { Text("远程目录") },
                placeholder = { Text("SafePassword") },
                singleLine = true,
                colors = safeFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )

            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("保存后自动同步")
                        Text(
                            "每次修改条目后延迟上传",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = autoSync,
                        onCheckedChange = {
                            autoSync = it
                            viewModel.setAutoSync(it)
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "上次同步：${formatDate(state.activeMeta?.lastSyncedAt ?: 0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (state.syncStatus.isNotBlank()) {
                    Text(state.syncStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            if (restore) {
                Button(
                    onClick = {
                        if (!currentConfig().isConfigured()) {
                            viewModel.showError("请填写以 http:// 或 https:// 开头的服务器地址")
                        } else {
                            viewModel.setWebDav(currentConfig())
                            masterError = false
                            masterPassword = ""
                            showMasterPrompt = true
                        }
                    },
                    enabled = !state.busy && baseUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存并立即同步")
                }
            } else {
                OutlinedButton(
                    onClick = {
                        viewModel.setWebDav(currentConfig())
                        viewModel.syncNow()
                    },
                    enabled = !state.busy && baseUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Sync, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("保存并立即同步")
                }
                OutlinedButton(
                    onClick = {
                        if (!currentConfig().isConfigured()) {
                            viewModel.showError("请填写以 http:// 或 https:// 开头的服务器地址")
                        } else {
                            showPullConfirm = true
                        }
                    },
                    enabled = !state.busy && baseUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Cloud, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("拉取云端数据（覆盖本地）")
                }
            }

            Text(
                "示例：坚果云填 https://dav.jianguoyun.com/dav/，用户名填账号邮箱，" +
                    "密码填“第三方应用管理”生成的应用密码。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "如果 NAS 只提供 HTTP，本应用允许明文 HTTP 连接；建议仅在可信局域网使用，或为 NAS 配置 HTTPS。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPullConfirm) {
        AlertDialog(
            onDismissRequest = { showPullConfirm = false },
            title = { Text("拉取云端数据") },
            text = {
                Text(
                    "将用云端密码库覆盖本地密码库。本地尚未同步的修改会丢失，是否继续？"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPullConfirm = false
                        viewModel.setWebDav(currentConfig())
                        viewModel.pullFromCloud()
                    }
                ) {
                    Text("覆盖本地")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPullConfirm = false }) { Text("取消") }
            }
        )
    }

    if (showMasterPrompt) {
        AlertDialog(
            onDismissRequest = { showMasterPrompt = false },
            title = { Text("输入主密码") },
            text = {
                Column {
                    Text(
                        "将从云端下载密码库并用这个主密码解密，恢复为本地密码库。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    PasswordField(
                        value = masterPassword,
                        onValueChange = {
                            masterPassword = it
                            masterError = false
                        },
                        label = "云端密码库的主密码",
                        visible = masterVisible,
                        onToggleVisibility = { masterVisible = !masterVisible }
                    )
                    if (masterError) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "请输入至少 8 位的主密码",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (masterPassword.length < 8) {
                            masterError = true
                        } else {
                            showMasterPrompt = false
                            viewModel.restoreFromCloud(currentConfig(), masterPassword)
                        }
                    }
                ) {
                    Text("下载并恢复")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMasterPrompt = false }) { Text("取消") }
            }
        )
    }
}
