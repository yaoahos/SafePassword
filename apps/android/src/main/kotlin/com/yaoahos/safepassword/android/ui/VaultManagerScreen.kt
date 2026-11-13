package com.yaoahos.safepassword.android.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yaoahos.safepassword.core.model.VaultMeta
import com.yaoahos.safepassword.core.sync.CloudVaultEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultManagerScreen(viewModel: AppViewModel, state: AppUiState) {
    var showCreate by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<VaultMeta?>(null) }
    var switchTarget by remember { mutableStateOf<CloudVaultEntry?>(null) }
    var cloudDeleteTarget by remember { mutableStateOf<CloudVaultEntry?>(null) }
    var switchPassword by remember { mutableStateOf("") }
    var switchVisible by remember { mutableStateOf(false) }
    var switchError by remember { mutableStateOf(false) }
    // 云端清单里的库全部列出（包括已经下载到本机的）：本机删除不会动云端，
    // 要清云端只能在对应条目上单独删，所以这个入口不能因为「本地已有」而藏起来。
    val localCloudKeys = state.vaults.map { it.cloudKey }.filter { it.isNotBlank() }.toSet()

    // 进入管理页时顺带拉一次云端清单：另一台设备新建的库会出现在这里，切换时才需要主密码。
    LaunchedEffect(state.webDav.isConfigured()) {
        if (state.webDav.isConfigured()) viewModel.loadCloudVaults(state.webDav)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { viewModel.goBack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("密码库管理") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberEntryScrollState())
                                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("新建密码库")
            }

            state.vaults.forEach { meta ->
                val isActive = meta.id == state.activeMeta?.id
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectVault(meta) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Outlined.Folder,
                            contentDescription = null,
                            tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(meta.name, fontWeight = FontWeight.Medium)
                                if (isActive) {
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "当前",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Text(
                                "上次打开：${formatDate(meta.lastOpenedAt)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { viewModel.selectVault(meta) }) {
                            Text("打开")
                        }
                        IconButton(onClick = { deleteTarget = meta }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除")
                        }
                    }
                }
            }

            if (state.cloudVaults.isNotEmpty()) {
                Text(
                    "云端密码库",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                state.cloudVaults.forEach { entry ->
                    val onDevice = entry.key.isNotBlank() && localCloudKeys.contains(entry.key)
                    SectionCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !onDevice) {
                                    switchTarget = entry
                                    switchPassword = ""
                                    switchError = false
                                    switchVisible = false
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Cloud, contentDescription = null)
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.name, fontWeight = FontWeight.Medium)
                                    if (onDevice) {
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            "本机已有",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Text(
                                    if (entry.updatedAt > 0) "云端 · ${formatDate(entry.updatedAt)}"
                                    else "云端 · 单文件",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!onDevice) {
                                TextButton(onClick = {
                                    switchTarget = entry
                                    switchPassword = ""
                                    switchError = false
                                    switchVisible = false
                                }) { Text("切换到") }
                            }
                            IconButton(onClick = { cloudDeleteTarget = entry }) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "删除云端密码库",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            Text(
                "每个密码库有独立的主密码和文件。WebDAV 同步时按密码库 ID 分开存放。删除本地库不会删除云端副本，反之亦然。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showCreate) {
        CreateVaultDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, password ->
                showCreate = false
                viewModel.createVault(name, password)
            }
        )
    }

    deleteTarget?.let { meta ->
        ConfirmDialog(
            title = "删除本地密码库",
            text = "将只删除「${meta.name}」在本机的文件，云端副本会保留。无法撤销，确定继续？",
            confirmText = "删除",
            destructive = true,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                viewModel.deleteVault(meta)
            }
        )
    }

    switchTarget?.let { entry ->
        AlertDialog(
            onDismissRequest = { switchTarget = null },
            title = { Text("切换到云端密码库") },
            text = {
                Column(Modifier.imePadding()) {
                    Text(
                        "将把「${entry.name}」下载到本机并切换过去。请输入它的主密码。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    PasswordField(
                        value = switchPassword,
                        onValueChange = {
                            switchPassword = it
                            switchError = false
                        },
                        label = "该密码库的主密码",
                        visible = switchVisible,
                        onToggleVisibility = { switchVisible = !switchVisible }
                    )
                    if (switchError) {
                        Spacer(Modifier.height(6.dp))
                        InlineNotice(
                            text = "主密码至少 6 位",
                            accent = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (switchPassword.length < 8) {
                        switchError = true
                    } else {
                        val password = switchPassword
                        switchTarget = null
                        switchPassword = ""
                        viewModel.restoreFromCloud(state.webDav, entry.key, entry.name, password)
                    }
                }) { Text("下载并切换") }
            },
            dismissButton = { TextButton(onClick = { switchTarget = null }) { Text("取消") } }
        )
    }
    cloudDeleteTarget?.let { entry ->
        ConfirmDialog(
            title = "删除云端密码库",
            text = "将从云端删除「${entry.name}」的密码库文件，其他设备将无法再从云端恢复它。本机已下载的副本不受影响。",
            confirmText = "删除",
            destructive = true,
            onDismiss = { cloudDeleteTarget = null },
            onConfirm = {
                cloudDeleteTarget = null
                viewModel.deleteCloudVault(entry)
            }
        )
    }
}